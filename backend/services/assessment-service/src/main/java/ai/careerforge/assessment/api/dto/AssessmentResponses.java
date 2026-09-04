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
     * JD-fit result. ATS scoring was dropped with resume generation (ADR-033); see {@link
     * AtsAssessmentResponse} (ADR-040) for its narrower, pre-render revival.
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

    public record AtsCheckResponse(String name, String label, double passRatio, double weight) {
    }

    /**
     * ATS structural score (ADR-040) — scored from the same pre-render, cited-evidence content
     * {@code ResumeRenderService} assembles for {@code render-service}, never a rendered
     * document, so it is available whether or not the render call that follows succeeds.
     */
    public record AtsAssessmentResponse(
            String jobDescriptionId,
            double atsScore,
            List<AtsCheckResponse> checks,
            Instant assessedAt) {
    }
}
