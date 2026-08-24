package ai.careerforge.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiRequests.RequirementInput;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.client.AiChatClient;
import ai.careerforge.ai.client.AiProvider;
import ai.careerforge.ai.client.GroqException;
import ai.careerforge.ai.prompt.PromptRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link JdOptimizationService} — requirement adjudication (ADR-038, superseding ADR-033's
 * single, larger call). Since ADR-038 this stage judges only; it no longer produces keywords,
 * missingRequirements, emphasis or free-text rationale — those are computed deterministically
 * downstream (jd-service's {@code OptimizationMerge}) from this result plus the JD analysis
 * already on hand. What still matters here: no candidate-facing value survives unless the
 * supplied evidence/requirement ids actually contain it, and the call reserves a tight
 * completion-token ceiling rather than the old blanket 4,096.
 */
class JdOptimizationServiceTest {

    private static final PromptRegistry.Prompt PROMPT =
            new PromptRegistry.Prompt("jd-optimization", 2, "You adjudicate requirements against evidence.");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiChatClient aiChatClient;
    private AiGenerationSupport support;
    private JdOptimizationService service;

    @BeforeEach
    void setUp() {
        aiChatClient = mock(AiChatClient.class);
        support = mock(AiGenerationSupport.class);
        service = new JdOptimizationService(aiChatClient, support);

        when(support.resolvePrompt("jd-optimization", null)).thenReturn(PROMPT);
    }

    private AiRequests.JdOptimizationRequest request() {
        return new AiRequests.JdOptimizationRequest(
                "Backend Engineer", "Acme Corp", "Senior",
                List.of(new RequirementInput("REQ-001", "5 years of Java", "HARD_REQUIRED", 5),
                        new RequirementInput("REQ-002", "Kafka in production", "PREFERRED", 3)),
                List.of(),
                List.of(new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Acme",
                        "Built Java services.", List.of("Java"), List.of(), "2019", "Present")),
                null);
    }

    /** Stubs {@code completeAndValidate} to return the model's own (already-parsed) JSON, so
     *  these tests exercise this service's id-stripping rather than re-testing
     *  {@code AiGenerationSupport}/{@code SchemaValidator}. */
    private void modelReturns(String json) throws Exception {
        JsonNode parsed = objectMapper.readTree(json);
        AiChatClient.AiChatResult raw = new AiChatClient.AiChatResult(json, "openai/gpt-oss-120b", 42, AiProvider.GROQ);
        when(support.completeAndValidate(eq(aiChatClient), eq(PROMPT.body()), anyString(),
                eq("jd-optimization"), eq("jd-optimization.schema.json"), eq(1_000)))
                .thenReturn(new AiGenerationSupport.ValidatedCompletion(parsed, raw, false));
    }

    @Nested
    class Output {

        @Test
        void returnsMatchesOnlyWithProvenance() throws Exception {
            modelReturns("""
                    {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"confidence":0.9,"matchKind":"STRONG"}]}""");

            AiResponses.JdOptimizationResponse response = service.optimise(request());

            JsonNode out = response.optimisation();
            assertThat(out.path("matches")).hasSize(1);
            assertThat(out.path("matches").get(0).path("matchKind").asText()).isEqualTo("STRONG");
            assertThat(response.provenance().promptVersion()).isEqualTo("jd-optimization@v2");
            assertThat(response.provenance().model()).isEqualTo("openai/gpt-oss-120b");
        }

        @Test
        @DisplayName("ADR-039: provenance records which provider actually served the request")
        void provenanceRecordsWhicheverProviderServedIt() throws Exception {
            JsonNode parsed = objectMapper.readTree("""
                    {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");
            AiChatClient.AiChatResult fromGemini =
                    new AiChatClient.AiChatResult(parsed.toString(), "gemini-2.5-flash", 30, AiProvider.GEMINI);
            when(support.completeAndValidate(any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(new AiGenerationSupport.ValidatedCompletion(parsed, fromGemini, false));

            AiResponses.JdOptimizationResponse response = service.optimise(request());

            assertThat(response.provenance().generatedBy()).isEqualTo("GEMINI");
        }

        @Test
        @DisplayName("requirements and evidence are fenced as untrusted data — never raw JD text, never the whole keyword list")
        void fencesRequirementsAndEvidenceOnly() throws Exception {
            modelReturns("""
                    {"matches":[]}""");

            service.optimise(request());

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(support).completeAndValidate(eq(aiChatClient), eq(PROMPT.body()), captor.capture(),
                    eq("jd-optimization"), eq("jd-optimization.schema.json"), eq(1_000));
            assertThat(captor.getValue()).contains("REQUIREMENTS").contains("EVIDENCE")
                    .doesNotContain("JOB_CONTEXT").doesNotContain("KEYWORDS");
        }

        @Test
        void providerFailurePropagatesUnchanged() {
            when(support.completeAndValidate(any(), any(), any(), any(), any(), anyInt()))
                    .thenThrow(new GroqException("Groq unavailable after 2 retries", true));

            assertThatThrownBy(() -> service.optimise(request())).isInstanceOf(GroqException.class);
        }

        @Test
        @DisplayName("calls the provider exactly once — no regenerate loop, since a repair (if any) is inside completeAndValidate")
        void isSingleShot() throws Exception {
            modelReturns("""
                    {"matches":[]}""");

            service.optimise(request());

            verify(support).completeAndValidate(any(), any(), any(), any(), any(), anyInt());
        }
    }

    @Nested
    class Grounding {

        @Test
        @DisplayName("evidence ids the profile doesn't contain are stripped, downgrading the match to NONE")
        void stripsHallucinatedEvidenceIds() throws Exception {
            modelReturns("""
                    {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004","PROJ-777"],"matchKind":"STRONG"}]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(ids(out.path("matches").get(0).path("evidenceIds"))).containsExactly("EXP-004");
        }

        @Test
        @DisplayName("a match left with no real evidence is downgraded to NONE, not left claiming STRONG")
        void downgradesUnsupportedMatches() throws Exception {
            modelReturns("""
                    {"matches":[{"requirementId":"REQ-002","evidenceIds":["EXP-999"],"matchKind":"STRONG"}]}""");

            JsonNode match = service.optimise(request()).optimisation().path("matches").get(0);

            assertThat(match.path("evidenceIds")).isEmpty();
            assertThat(match.path("matchKind").asText()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("an entry keyed by a requirement this posting never had is dropped entirely")
        void dropsUnknownRequirementIds() throws Exception {
            modelReturns("""
                    {"matches":[{"requirementId":"REQ-999","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(out.path("matches")).isEmpty();
        }

        @Test
        @DisplayName("every cited id in the final result exists in the supplied inventory")
        void citedEvidenceIdsAreAllReal() throws Exception {
            modelReturns("""
                    {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004","EXP-999"],"matchKind":"STRONG"}]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(JdOptimizationService.citedEvidenceIds(out)).containsExactly("EXP-004");
        }
    }

    private static List<String> ids(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
