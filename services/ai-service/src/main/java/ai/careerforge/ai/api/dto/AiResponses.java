package ai.careerforge.ai.api.dto;

import ai.careerforge.ai.grounding.GroundingReport;
import com.fasterxml.jackson.databind.JsonNode;

/** Response bodies for the internal AI endpoints. */
public final class AiResponses {

    private AiResponses() {
    }

    /**
     * Every AI response carries its provenance so the calling service can persist it on the
     * artifact. Without model id and prompt version, a generated resume cannot be
     * reproduced or explained later.
     *
     * @param promptVersion e.g. {@code resume-content@v1}
     * @param model         the model that actually served the request
     * @param totalTokens   for cost attribution
     * @param regenerated   true when the first attempt failed validation and was retried
     */
    public record Provenance(String promptVersion, String model, int totalTokens,
                             boolean regenerated) {
    }

    public record JdAnalysisResponse(JsonNode analysis, Provenance provenance) {
    }

    public record EvidenceSelectionResponse(JsonNode selection, Provenance provenance) {
    }

    /**
     * @param content         validated resume content
     * @param grounding       the full verification report, stored with the resume version
     * @param removedSections statements dropped because they failed grounding twice; the
     *                        caller surfaces these to the user as unmet requirements
     */
    public record ResumeContentResponse(JsonNode content, GroundingReport grounding,
                                        java.util.List<String> removedSections,
                                        Provenance provenance) {
    }

    /**
     * @param content            validated cover-letter content
     * @param grounding          the full verification report, stored with the version
     * @param removedParagraphs  paragraphs dropped because they failed grounding twice; the
     *                           caller surfaces the letter with those simply absent
     */
    public record CoverLetterContentResponse(JsonNode content, GroundingReport grounding,
                                             java.util.List<String> removedParagraphs,
                                             Provenance provenance) {
    }

    /**
     * @param content           validated email content (greeting, one body paragraph, one
     *                          closing paragraph, sign-off) — application-service assembles
     *                          the final subject and body around this; see
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
