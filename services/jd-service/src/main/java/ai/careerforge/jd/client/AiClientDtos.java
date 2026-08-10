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
}
