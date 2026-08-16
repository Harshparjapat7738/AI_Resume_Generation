package ai.careerforge.jd.api.dto;

import java.time.Instant;
import java.util.List;

public final class JdResponses {

    private JdResponses() {
    }

    public record JdSummaryResponse(
            String id, String status, String sourceType, String title, String company, Instant createdAt) {
    }

    /**
     * {@code location}/{@code skillsSummary}/{@code experienceSummary} are only ever
     * non-null for a {@code sourceType = URL} job description whose page embedded
     * schema.org {@code JobPosting} data (see ADR-015) — otherwise null, never guessed.
     */
    public record JdDetailResponse(
            String id, String status, String sourceType, String sourceUrl,
            String title, String company, String location, String skillsSummary, String experienceSummary,
            String rawText, int currentVersion, Instant createdAt) {
    }

    public record RequirementResponse(
            String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    public record JdAnalysisResponse(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<RequirementResponse> requirements) {
    }

    /**
     * The JD-optimization result (ADR-033) — the product's primary deliverable. {@code
     * optimisation} is ai-service's already-validated JSON, republished verbatim; {@code
     * citedEvidenceIds} is the provenance trail proving every candidate-facing value traces back
     * to the caller's own profile. Never the Mongo entity.
     */
    public record JdOptimizationResponse(
            String id,
            String jobDescriptionId,
            java.util.Map<String, Object> optimisation,
            java.util.List<String> citedEvidenceIds,
            java.time.Instant createdAt) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
