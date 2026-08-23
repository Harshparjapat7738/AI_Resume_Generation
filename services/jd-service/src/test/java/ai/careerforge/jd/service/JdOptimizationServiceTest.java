package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.client.AiClientDtos.JdOptimizationRequest;
import ai.careerforge.jd.client.AiClientDtos.JdOptimizationResponse;
import ai.careerforge.jd.client.AiServiceClient;
import ai.careerforge.jd.client.ProfileServiceClient;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.JdOptimization;
import ai.careerforge.jd.domain.JobDescription;
import ai.careerforge.jd.domain.Requirement;
import ai.careerforge.jd.repository.JdOptimizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The JD-optimization orchestration (ADR-033, restructured by ADR-038): input resolution,
 * deterministic evidence pre-filtering, at most one adjudication call, deterministic merge,
 * caching, persistence, single-flight, and error mapping. ai-service's own output filtering is
 * tested there; this covers the call-count budget ADR-038 sets and everything jd-service adds
 * around the adjudication call.
 *
 * <p>{@link EvidenceMatcher} and {@link OptimizationMerge} are used as real instances — they are
 * pure, deterministic and side-effect-free, so mocking them would only re-describe their own
 * logic instead of testing this class's orchestration of them. {@link SingleFlightLock} is real
 * too, backed by an unstubbed {@link StringRedisTemplate} mock: calling it throws (no Redis to
 * connect to), which exercises the lock's own documented degrade-to-uncoordinated behaviour —
 * exactly what every test here needs, since none of them care about lock contention itself.
 */
class JdOptimizationServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JD_ID = "jd-1";
    private static final String JD_VERSION_ID = "jdv-1";

    private JdService jdService;
    private ProfileServiceClient profileServiceClient;
    private AiServiceClient aiServiceClient;
    private JdOptimizationRepository optimizations;
    private JdOptimizationService service;

    @BeforeEach
    void setUp() {
        jdService = mock(JdService.class);
        profileServiceClient = mock(ProfileServiceClient.class);
        aiServiceClient = mock(AiServiceClient.class);
        optimizations = mock(JdOptimizationRepository.class);
        StringRedisTemplate unreachableRedis = mock(StringRedisTemplate.class);
        service = new JdOptimizationService(jdService, profileServiceClient, aiServiceClient,
                optimizations, new ObjectMapper(), new EvidenceMatcher(), new OptimizationMerge(),
                new SingleFlightLock(unreachableRedis, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        JdAnalysis analysis = new JdAnalysis(JD_ID, JD_VERSION_ID, "Backend Engineer", "Acme", "Senior",
                List.of("Java", "Kafka"),
                List.of(new Requirement("REQ-001", "5 years of Java", "HARD_REQUIRED", 5, List.of("Java"))),
                "jd-analysis@v2", "openai/gpt-oss-120b");
        when(jdService.analyse(USER_ID, JD_ID)).thenReturn(analysis);
        when(jdService.requireOwned(USER_ID, JD_ID)).thenReturn(mock(JobDescription.class));
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.empty());
        when(optimizations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // "Backend Engineer Acme Built Java services. Java" — lexically overlaps REQ-001's
        // "Java" normalised term, so it is a real candidate, not a zero-candidate gap.
        when(profileServiceClient.getEvidence()).thenReturn(List.of(new EvidenceItem(
                "EXP-004", "EXPERIENCE", "Backend Engineer", "Acme", "Built Java services.",
                List.of("Java"), List.of(), "2019", "Present")));
    }

    private void aiReturns(String matchesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        when(aiServiceClient.optimise(any())).thenReturn(new JdOptimizationResponse(
                mapper.readTree(matchesJson),
                mapper.readTree("{\"promptVersion\":\"jd-optimization@v2\",\"model\":\"openai/gpt-oss-120b\"}")));
    }

    @Test
    void persistsTheOptimizationWithItsEvidenceProvenance() throws Exception {
        aiReturns("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"confidence":0.9,"matchKind":"STRONG"}]}""");

        JdOptimization result = service.optimise(USER_ID, JD_ID, false);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.jdVersionId()).isEqualTo(JD_VERSION_ID);
        assertThat(result.optimisation()).containsKey("keywords");
        assertThat(result.optimisation()).containsEntry("derived", false);
        assertThat(result.citedEvidenceIds()).containsExactly("EXP-004");
        assertThat(result.promptVersion()).isEqualTo("jd-optimization@v2");
        verify(optimizations).save(any());
    }

    @Test
    @DisplayName("ADR-038: a first-time (cold) optimization spends at most one adjudication call")
    void coldPathIsAtMostOneAdjudicationCall() throws Exception {
        aiReturns("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");

        service.optimise(USER_ID, JD_ID, false);

        verify(aiServiceClient, times(1)).optimise(any());
    }

    @Test
    @DisplayName("ADR-038: with the analysis already warm and no cached optimization, exactly one "
            + "adjudication call is made — not zero, not two")
    void warmAnalysisNoCachedOptimizationMakesExactlyOneAdjudicationCall() throws Exception {
        // jdService is mocked, so JdService#analyse's own cache-through (proven separately in
        // JdServiceTest) is not re-exercised here — from this class's perspective, "warm
        // analysis" simply means analyse() already returned without this class spending a call
        // on it. What this class controls, and what this test asserts, is that exactly one
        // adjudication call follows.
        aiReturns("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");

        service.optimise(USER_ID, JD_ID, false);

        verify(aiServiceClient, times(1)).optimise(any());
        verify(optimizations, times(1)).save(any());
    }

    @Test
    @DisplayName("ADR-038: only the deterministically filtered evidence subset reaches ai-service, never the full inventory")
    void onlyFilteredEvidenceReachesAiService() throws Exception {
        // 6 items: 1 lexically relevant to REQ-001 ("Java"), 5 not (unrelated skills).
        List<EvidenceItem> fullInventory = new java.util.ArrayList<>();
        fullInventory.add(new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Acme",
                "Built Java services.", List.of("Java"), List.of(), "2019", "Present"));
        for (int i = 0; i < 5; i++) {
            fullInventory.add(new EvidenceItem("SKILL-00" + i, "SKILL", "Watercolor painting " + i,
                    null, "Portrait painting", List.of(), List.of(), null, null));
        }
        when(profileServiceClient.getEvidence()).thenReturn(fullInventory);
        aiReturns("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");

        service.optimise(USER_ID, JD_ID, false);

        var captor = org.mockito.ArgumentCaptor.forClass(JdOptimizationRequest.class);
        verify(aiServiceClient).optimise(captor.capture());
        List<EvidenceItem> sent = captor.getValue().evidence();
        assertThat(sent).hasSizeLessThan(fullInventory.size());
        assertThat(sent).extracting(EvidenceItem::evidenceId).contains("EXP-004")
                .doesNotContain("SKILL-000", "SKILL-001", "SKILL-002", "SKILL-003", "SKILL-004");
    }

    @Test
    @DisplayName("ADR-038: a requirement with no lexical candidate never reaches the adjudication call at all")
    void zeroCandidateRequirementsSkipTheAdjudicationCallEntirely() {
        // Evidence about an entirely different domain — no lexical overlap with "5 years of Java".
        when(profileServiceClient.getEvidence()).thenReturn(List.of(new EvidenceItem(
                "SKILL-001", "SKILL", "Watercolor painting", null, "Portrait painting",
                List.of(), List.of(), null, null)));

        JdOptimization result = service.optimise(USER_ID, JD_ID, false);

        verify(aiServiceClient, never()).optimise(any());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missing = (List<Map<String, Object>>) result.optimisation().get("missingRequirements");
        assertThat(missing).extracting(m -> m.get("requirementId")).containsExactly("REQ-001");
    }

    @Test
    @DisplayName("ADR-038: adding a skill is a deterministic patch — zero Groq calls, marked derived/stale")
    void skillGapPatchNeverCallsGroq() {
        JdOptimization existing = new JdOptimization(USER_ID, JD_ID, JD_VERSION_ID,
                new java.util.LinkedHashMap<>(Map.of("requirementMatches", List.of(), "keywords", List.of(),
                        "missingRequirements", List.of(Map.of("requirementId", "REQ-001", "note", "gap")),
                        "emphasis", List.of())),
                List.of(), "jd-optimization@v2", "openai/gpt-oss-120b");
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.of(existing));

        JdOptimization patched = service.patchWithLatestEvidence(USER_ID, JD_ID);

        verify(aiServiceClient, never()).optimise(any());
        assertThat(patched.optimisation()).containsEntry("derived", true);
        assertThat(patched.optimisation()).containsEntry("stale", true);
        // The newly-fetched evidence lexically overlaps REQ-001, so it is promoted out of missing.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) patched.optimisation().get("requirementMatches");
        assertThat(matches).extracting(m -> m.get("requirementId")).contains("REQ-001");
    }

    @Test
    @DisplayName("patching before any optimization exists is rejected rather than fabricating one")
    void patchingWithNoExistingOptimizationIsRejected() {
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchWithLatestEvidence(USER_ID, JD_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("whatever JdService.analyse throws is delegated, never re-implemented (ADR-037: no confirm gate anymore)")
    void analysisErrorsComeFromJdService() {
        when(jdService.analyse(USER_ID, JD_ID)).thenThrow(ApiException.notOwned());

        assertThatThrownBy(() -> service.optimise(USER_ID, JD_ID, false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(aiServiceClient, never()).optimise(any());
    }

    @Test
    void anotherUsersOptimizationIsNotFound() {
        when(optimizations.findByIdAndUserId("opt-1", USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned(USER_ID, "opt-1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("an existing result for the same JD version is reused rather than re-spending an AI request")
    void cachesByJdVersion() {
        JdOptimization existing = new JdOptimization(USER_ID, JD_ID, JD_VERSION_ID,
                Map.of("keywords", List.of()), List.of("EXP-004"), "jd-optimization@v2", "m");
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.of(existing));

        JdOptimization result = service.optimise(USER_ID, JD_ID, false);

        assertThat(result).isSameAs(existing);
        verify(aiServiceClient, never()).optimise(any());
        verify(profileServiceClient, never()).getEvidence();
    }

    @Test
    @DisplayName("refresh recomputes and replaces in place — one current result per JD version")
    void refreshReplacesRatherThanAccumulating() throws Exception {
        JdOptimization existing = new JdOptimization(USER_ID, JD_ID, JD_VERSION_ID,
                new java.util.LinkedHashMap<>(Map.of("keywords", List.of())), List.of(), "old@v1", "old-model");
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.of(existing));
        aiReturns("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");

        JdOptimization result = service.optimise(USER_ID, JD_ID, true);

        assertThat(result).isSameAs(existing);
        assertThat(result.citedEvidenceIds()).containsExactly("EXP-004");
        assertThat(result.promptVersion()).isEqualTo("jd-optimization@v2");
        verify(optimizations).save(existing);
    }

    @Test
    void anEmptyProfileIsRejectedBeforeSpendingAnAiRequest() {
        when(profileServiceClient.getEvidence()).thenReturn(List.of());

        assertThatThrownBy(() -> service.optimise(USER_ID, JD_ID, false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(aiServiceClient, never()).optimise(any());
    }

    @Test
    @DisplayName("an AI failure surfaces as AI_GENERATION_FAILED without leaking the provider's body")
    void aiFailureIsMappedAndNothingIsPersisted() {
        when(aiServiceClient.optimise(any(JdOptimizationRequest.class)))
                .thenThrow(feign.FeignException.errorStatus("optimise",
                        feign.Response.builder().status(500).reason("boom")
                                .request(feign.Request.create(feign.Request.HttpMethod.POST, "/", java.util.Map.of(),
                                        null, null, null))
                                .build()));

        assertThatThrownBy(() -> service.optimise(USER_ID, JD_ID, false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AI_GENERATION_FAILED);

        verify(optimizations, never()).save(any());
    }

    @Test
    void profileServiceBeingDownIsReportedAsUpstreamUnavailable() {
        when(profileServiceClient.getEvidence())
                .thenThrow(feign.FeignException.errorStatus("getEvidence",
                        feign.Response.builder().status(503).reason("down")
                                .request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Map.of(),
                                        null, null, null))
                                .build()));

        assertThatThrownBy(() -> service.optimise(USER_ID, JD_ID, false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
}
