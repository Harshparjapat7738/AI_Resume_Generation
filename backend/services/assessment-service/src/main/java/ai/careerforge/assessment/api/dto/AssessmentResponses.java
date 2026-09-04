package ai.careerforge.assessment.api.dto;

import java.time.Instant;
import java.util.List;

public final class AssessmentResponses {

    private AssessmentResponses() {
    }

    public record RequirementMatchResponse(
            String requirementId, String text, String type, String matchStrength, List<String> evidenceIds) {
    }

    public record RecommendationResponse(String type, String severity, String message, String relatedRequirementId) {
    }

    /**
     * JD-fit result. ATS scoring was dropped with resume generation (ADR-033) — every check it
     * ran read a rendered resume's structure, and no resume is produced any more.
     */
    public record AssessmentResponse(
            String jdOptimizationId,
            String jobDescriptionId,
            double compatibilityScore,
            double coverage,
            double keywordMatch,
            double seniorityMatch,
            double recency,
            List<RequirementMatchResponse> requirementMatches,
            List<RequirementMatchResponse> unmetHardRequirements,
            List<String> matchedKeywords,
            List<String> missingKeywords,
            String readinessBand,
            String bandRule,
            List<RecommendationResponse> recommendations,
            Instant assessedAt) {
    }
}
