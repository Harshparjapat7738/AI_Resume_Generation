package ai.careerforge.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 * {@link JdOptimizationService} — the operation that replaced resume/cover-letter content
 * generation (ADR-033). The properties that matter here are that it produces targeting data
 * rather than prose, and that no candidate-facing value survives unless the supplied evidence
 * inventory actually contains it.
 */
class JdOptimizationServiceTest {

    private static final PromptRegistry.Prompt PROMPT =
            new PromptRegistry.Prompt("jd-optimization", 1, "You analyse a JD against evidence.");

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
                List.of("Java", "Kafka"),
                List.of(new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Acme",
                        "Built Java services.", List.of("Java"), List.of(), "2019", "Present")),
                null);
    }

    /** Stubs schema validation to return the model's own JSON, so these tests exercise this
     *  service's filtering rather than re-testing {@code SchemaValidator}. */
    private void modelReturns(String json) throws Exception {
        when(aiChatClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new AiChatClient.AiChatResult(json, "openai/gpt-oss-120b", 42));
        when(support.validateSchema(eq(json), eq("jd-optimization.schema.json"), eq("jd-optimization")))
                .thenReturn(objectMapper.readTree(json));
    }

    @Nested
    class Output {

        @Test
        void returnsTargetingDataWithProvenance() throws Exception {
            modelReturns("""
                    {"targetRole":"Backend Engineer","targetCompany":"Acme Corp",
                     "keywords":[{"term":"Java","priority":"REQUIRED","category":"TECHNICAL_SKILL","evidenceIds":["EXP-004"]}],
                     "requirementMatches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchStrength":"STRONG","reason":"Java services"}],
                     "missingRequirements":[{"requirementId":"REQ-002","note":"No Kafka evidence"}],
                     "emphasis":[{"evidenceId":"EXP-004","rank":1,"rationale":"Directly relevant"}]}""");

            AiResponses.JdOptimizationResponse response = service.optimise(request());

            JsonNode out = response.optimisation();
            assertThat(out.path("targetRole").asText()).isEqualTo("Backend Engineer");
            assertThat(out.path("keywords")).hasSize(1);
            assertThat(out.path("requirementMatches").get(0).path("matchStrength").asText()).isEqualTo("STRONG");
            assertThat(out.path("missingRequirements").get(0).path("requirementId").asText()).isEqualTo("REQ-002");
            assertThat(response.provenance().promptVersion()).isEqualTo("jd-optimization@v1");
            assertThat(response.provenance().model()).isEqualTo("openai/gpt-oss-120b");
        }

        @Test
        @DisplayName("the JD, its keywords and the evidence are all fenced as untrusted data")
        void fencesEveryUntrustedBlock() throws Exception {
            modelReturns("""
                    {"keywords":[],"requirementMatches":[],"missingRequirements":[],"emphasis":[]}""");

            service.optimise(request());

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(aiChatClient).complete(eq(PROMPT.body()), captor.capture(), eq("jd-optimization"));
            assertThat(captor.getValue()).contains("JOB_CONTEXT").contains("KEYWORDS").contains("EVIDENCE");
        }

        @Test
        void providerFailurePropagatesUnchanged() {
            when(aiChatClient.complete(anyString(), anyString(), anyString()))
                    .thenThrow(new GroqException("Groq unavailable after 2 retries", true));

            assertThatThrownBy(() -> service.optimise(request())).isInstanceOf(GroqException.class);
        }

        @Test
        @DisplayName("calls the provider exactly once — no regenerate loop, since there is no prose to correct")
        void isSingleShot() throws Exception {
            modelReturns("""
                    {"keywords":[],"requirementMatches":[],"missingRequirements":[],"emphasis":[]}""");

            service.optimise(request());

            verify(aiChatClient).complete(anyString(), anyString(), anyString());
        }
    }

    @Nested
    class Grounding {

        @Test
        @DisplayName("evidence ids the profile doesn't contain are stripped everywhere they appear")
        void stripsHallucinatedEvidenceIds() throws Exception {
            modelReturns("""
                    {"keywords":[{"term":"Java","priority":"REQUIRED","evidenceIds":["EXP-004","EXP-999"]}],
                     "requirementMatches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004","PROJ-777"],"matchStrength":"STRONG"}],
                     "missingRequirements":[],
                     "emphasis":[{"evidenceId":"EXP-004","rank":1},{"evidenceId":"CERT-123","rank":2}]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(ids(out.path("keywords").get(0).path("evidenceIds"))).containsExactly("EXP-004");
            assertThat(ids(out.path("requirementMatches").get(0).path("evidenceIds"))).containsExactly("EXP-004");
            assertThat(out.path("emphasis")).hasSize(1);
            assertThat(out.path("emphasis").get(0).path("evidenceId").asText()).isEqualTo("EXP-004");
        }

        @Test
        @DisplayName("a match left with no real evidence is downgraded to NONE, not left claiming STRONG")
        void downgradesUnsupportedMatches() throws Exception {
            modelReturns("""
                    {"keywords":[],
                     "requirementMatches":[{"requirementId":"REQ-002","evidenceIds":["EXP-999"],"matchStrength":"STRONG"}],
                     "missingRequirements":[],"emphasis":[]}""");

            JsonNode match = service.optimise(request()).optimisation().path("requirementMatches").get(0);

            assertThat(match.path("evidenceIds")).isEmpty();
            assertThat(match.path("matchStrength").asText()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("entries keyed by a requirement this posting never had are dropped entirely")
        void dropsUnknownRequirementIds() throws Exception {
            modelReturns("""
                    {"keywords":[],
                     "requirementMatches":[{"requirementId":"REQ-999","evidenceIds":["EXP-004"],"matchStrength":"STRONG"}],
                     "missingRequirements":[{"requirementId":"REQ-888","note":"invented"}],
                     "emphasis":[]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(out.path("requirementMatches")).isEmpty();
            assertThat(out.path("missingRequirements")).isEmpty();
        }

        @Test
        @DisplayName("an unsupported keyword is kept with no evidence — the gap is the point")
        void keepsUnsupportedKeywordsAsGaps() throws Exception {
            modelReturns("""
                    {"keywords":[{"term":"Kafka","priority":"PREFERRED","evidenceIds":[]}],
                     "requirementMatches":[],
                     "missingRequirements":[{"requirementId":"REQ-002","note":"No Kafka evidence"}],
                     "emphasis":[]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(out.path("keywords")).hasSize(1);
            assertThat(out.path("keywords").get(0).path("term").asText()).isEqualTo("Kafka");
            assertThat(out.path("keywords").get(0).path("evidenceIds")).isEmpty();
            assertThat(out.path("missingRequirements")).hasSize(1);
        }

        @Test
        @DisplayName("every cited id in the final result exists in the supplied inventory")
        void citedEvidenceIdsAreAllReal() throws Exception {
            modelReturns("""
                    {"keywords":[{"term":"Java","priority":"REQUIRED","evidenceIds":["EXP-004","EXP-999"]}],
                     "requirementMatches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchStrength":"STRONG"}],
                     "missingRequirements":[],
                     "emphasis":[{"evidenceId":"EXP-004","rank":1}]}""");

            JsonNode out = service.optimise(request()).optimisation();

            assertThat(JdOptimizationService.citedEvidenceIds(out)).containsExactly("EXP-004");
        }
    }

    private static List<String> ids(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
