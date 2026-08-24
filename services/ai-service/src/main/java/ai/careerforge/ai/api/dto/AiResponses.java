package ai.careerforge.ai.api.dto;

import ai.careerforge.ai.grounding.GroundingReport;
import com.fasterxml.jackson.databind.JsonNode;

/** Response bodies for the internal AI endpoints. */
public final class AiResponses {

    private AiResponses() {
    }

    /**
     * Every AI response carries its provenance so the calling service can persist it on the
     * artifact. Without model id and prompt version, a generated result cannot be reproduced
     * or explained later.
     *
     * @param promptVersion e.g. {@code jd-optimization@v1}
     * @param model         the model that actually served the request
     * @param totalTokens   for cost attribution
     * @param regenerated   true when the first attempt failed validation and was retried
     * @param generatedBy   which provider actually served the request — {@code GROQ} or
     *                      {@code GEMINI} (ADR-039), telemetry/debug only; nothing downstream
     *                      branches on it
     */
    public record Provenance(String promptVersion, String model, int totalTokens,
                             boolean regenerated, String generatedBy) {
    }

    public record JdAnalysisResponse(JsonNode analysis, Provenance provenance) {
    }

    public record EvidenceSelectionResponse(JsonNode selection, Provenance provenance) {
    }

    /**
     * The JD-optimization result (ADR-033): keywords, per-requirement verdicts, missing
     * requirements and emphasis ordering. No prose — see {@code JdOptimizationService}.
     *
     * <p>Carries no {@code GroundingReport}, deliberately: grounding validates generated
     * sentences against evidence, and this operation generates no sentences. Its equivalent
     * guarantee is structural — every candidate-facing value is an {@code evidenceId} the
     * service has already verified against the supplied inventory.
     */
    public record JdOptimizationResponse(JsonNode optimisation, Provenance provenance) {
    }

    /**
     * @param content           the grounded highlight paragraph(s), see
     *                          ARCHITECTURE_DECISIONS.md ADR-019
     * @param grounding         the full verification report, stored with the email version
     * @param removedParagraphs paragraphs dropped because they failed grounding twice
     */
    public record EmailContentResponse(JsonNode content, GroundingReport grounding,
                                       java.util.List<String> removedParagraphs,
                                       Provenance provenance) {
    }

    /**
     * Diagnostic payload. Deliberately reports whether a key is configured and what it
     * looks like in masked form — never the key itself.
     */
    public record StatusResponse(boolean groqConfigured, String maskedApiKey, String model,
                                 String baseUrl, java.util.Map<String, Integer> promptVersions,
                                 boolean reachable, String detail) {
    }
}
