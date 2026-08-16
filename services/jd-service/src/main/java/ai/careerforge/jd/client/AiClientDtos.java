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
