package ai.careerforge.assessment.api.dto;

import java.time.Instant;
import java.util.List;

public final class AssessmentResponses {

    private AssessmentResponses() {
    }

    public record CheckResponse(String checkId, String label, double weight, double passRatio, String detail, double earned) {
    }

    public record RequirementMatchResponse(
            String requirementId, String text, String type, String matchStrength, List<String> evidenceIds) {
    }

    public record RecommendationResponse(String type, String severity, String message, String relatedRequirementId) {
    }

    /** Combined ATS + JD-fit result — the two are always read together on the result page. */
    public record AssessmentResponse(
            String resumeVersionId,
            double atsScore,
            List<CheckResponse> atsChecks,
            String engineVersion,
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
