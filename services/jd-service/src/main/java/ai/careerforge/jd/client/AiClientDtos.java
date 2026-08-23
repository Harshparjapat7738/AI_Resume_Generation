package ai.careerforge.jd.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Local mirror of {@code ai.careerforge.ai.api.dto.AiRequests/AiResponses} for the one
 * endpoint jd-service calls. The two services share a JSON contract, not a Java module
 * (ADR-006) — field names must match {@code ai-service}'s DTOs exactly.
 */
public final class AiClientDtos {

    private AiClientDtos() {
    }

    public record JdAnalysisRequest(String jobDescriptionText, Integer promptVersion) {
    }

    public record JdAnalysisResponse(JsonNode analysis, JsonNode provenance) {
    }

    // ---- JD optimization (ADR-033) --------------------------------------

    /** Mirrors profile-service's evidence projection and ai-service's {@code EvidenceItem}
     *  field-for-field — the same shape resume-service already consumes. */
    public record EvidenceItem(
            String evidenceId, String type, String title, String organisation, String description,
            java.util.List<String> technologies, java.util.List<String> metrics,
            String startDate, String endDate) {

        /** Everything this item asserts, as one lower-cased searchable string — used only for
         *  jd-service's own deterministic lexical filtering/ranking (ADR-038), never sent
         *  anywhere; mirrors ai-service's identically-named method on its own copy of this
         *  shape. */
        public String searchableText() {
            String joined = String.join(" ",
                    nullToEmpty(title), nullToEmpty(organisation), nullToEmpty(description),
                    String.join(" ", technologies == null ? java.util.List.of() : technologies),
                    String.join(" ", metrics == null ? java.util.List.of() : metrics));
            return joined.toLowerCase(java.util.Locale.ROOT);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    public record RequirementInput(String requirementId, String text, String type, int weight) {
    }

    public record JdOptimizationRequest(
            String jobTitle, String company, String seniority,
            java.util.List<RequirementInput> requirements,
            java.util.List<String> keywords,
            java.util.List<EvidenceItem> evidence,
            Integer promptVersion) {
    }

    public record JdOptimizationResponse(JsonNode optimisation, JsonNode provenance) {
    }
}
