package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The JD-optimization orchestration (ADR-033): input resolution, the single AI call, caching,
 * persistence and error mapping. The AI's own output filtering is ai-service's job and is tested
 * there — this covers what jd-service adds around it.
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
        service = new JdOptimizationService(jdService, profileServiceClient, aiServiceClient,
                optimizations, new ObjectMapper());

        JdAnalysis analysis = new JdAnalysis(JD_ID, JD_VERSION_ID, "Backend Engineer", "Acme", "Senior",
                List.of("Java", "Kafka"),
                List.of(new Requirement("REQ-001", "5 years of Java", "HARD_REQUIRED", 5, List.of("Java"))),
                "jd-analysis@v1", "openai/gpt-oss-120b");
        when(jdService.analyse(USER_ID, JD_ID)).thenReturn(analysis);
        when(jdService.requireOwned(USER_ID, JD_ID)).thenReturn(mock(JobDescription.class));
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.empty());
        when(optimizations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileServiceClient.getEvidence()).thenReturn(List.of(new EvidenceItem(
                "EXP-004", "EXPERIENCE", "Backend Engineer", "Acme", "Built Java services.",
                List.of("Java"), List.of(), "2019", "Present")));
    }

    private void aiReturns(String optimisationJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        when(aiServiceClient.optimise(any())).thenReturn(new JdOptimizationResponse(
                mapper.readTree(optimisationJson),
                mapper.readTree("{\"promptVersion\":\"jd-optimization@v1\",\"model\":\"openai/gpt-oss-120b\"}")));
    }

    @Test
    void persistsTheOptimizationWithItsEvidenceProvenance() throws Exception {
        aiReturns("""
                {"targetRole":"Backend Engineer","keywords":[{"term":"Java","priority":"REQUIRED","evidenceIds":["EXP-004"]}],
                 "requirementMatches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchStrength":"STRONG"}],
                 "missingRequirements":[],"emphasis":[{"evidenceId":"EXP-004","rank":1}]}""");

        JdOptimization result = service.optimise(USER_ID, JD_ID, false);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.jdVersionId()).isEqualTo(JD_VERSION_ID);
        assertThat(result.optimisation()).containsKey("keywords");
        assertThat(result.citedEvidenceIds()).containsExactly("EXP-004");
        assertThat(result.promptVersion()).isEqualTo("jd-optimization@v1");
        verify(optimizations).save(any());
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
    void cachesByJdVersion() throws Exception {
        JdOptimization existing = new JdOptimization(USER_ID, JD_ID, JD_VERSION_ID,
                java.util.Map.of("keywords", List.of()), List.of("EXP-004"), "jd-optimization@v1", "m");
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
                java.util.Map.of("keywords", List.of()), List.of(), "old@v1", "old-model");
        when(optimizations.findByJdVersionId(JD_VERSION_ID)).thenReturn(Optional.of(existing));
        aiReturns("""
                {"keywords":[{"term":"Java","priority":"REQUIRED","evidenceIds":["EXP-004"]}],
                 "requirementMatches":[],"missingRequirements":[],"emphasis":[]}""");

        JdOptimization result = service.optimise(USER_ID, JD_ID, true);

        assertThat(result).isSameAs(existing);
        assertThat(result.citedEvidenceIds()).containsExactly("EXP-004");
        assertThat(result.promptVersion()).isEqualTo("jd-optimization@v1");
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
