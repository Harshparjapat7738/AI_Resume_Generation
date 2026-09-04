package ai.careerforge.assessment.api;

import ai.careerforge.assessment.api.dto.AssessmentResponses.AssessmentResponse;
import ai.careerforge.assessment.api.dto.AssessmentResponses.AtsAssessmentResponse;
import ai.careerforge.assessment.api.dto.AssessmentResponses.AtsCheckResponse;
import ai.careerforge.assessment.api.dto.AssessmentResponses.RecommendationResponse;
import ai.careerforge.assessment.api.dto.AssessmentResponses.RequirementMatchResponse;
import ai.careerforge.assessment.domain.AtsAssessment;
import ai.careerforge.assessment.domain.AtsCheckResult;
import ai.careerforge.assessment.domain.JdFitAssessment;
import ai.careerforge.assessment.domain.RecommendationItem;
import ai.careerforge.assessment.domain.RequirementMatchResult;
import ai.careerforge.assessment.service.AssessmentService;
import ai.careerforge.common.security.CallerId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * See docs/API_CATALOG.md &sect;3 (Milestone 7 — assessment-service). Scope deviation from
 * the blueprint's ten-check ATS engine — see ARCHITECTURE_DECISIONS.md ADR-014; ADR-040 revives
 * a narrower, pre-render version of it (see {@code AtsScoringEngine}).
 *
 * <p>Base path is {@code /api/assessment} (ADR-040 renamed it from the stale
 * {@code /api/assessment/resume-versions} left over from before ADR-033 rekeyed everything here
 * to {@code jobDescriptionId}) — the frontend's {@code assessmentApi.ts} already called this
 * exact shape.
 */
@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** Scores the JD optimization for this job description (ADR-033). Computes on first call;
     *  idempotent — returns the cached result thereafter. */
    @PostMapping("/{jobDescriptionId}")
    public ResponseEntity<AssessmentResponse> assess(@CallerId String userId, @PathVariable String jobDescriptionId) {
        return ResponseEntity.ok(toResponse(assessmentService.assess(userId, jobDescriptionId)));
    }

    @GetMapping("/{jobDescriptionId}")
    public ResponseEntity<AssessmentResponse> get(@CallerId String userId, @PathVariable String jobDescriptionId) {
        return ResponseEntity.ok(toResponse(assessmentService.requireExisting(userId, jobDescriptionId)));
    }

    /** ATS structural score for this job description (ADR-040) — scored from the same
     *  pre-render, cited-evidence content {@code ResumeRenderService} assembles in
     *  application-service, never a rendered document. Computes on first call; idempotent —
     *  returns the cached result thereafter. */
    @PostMapping("/ats/{jobDescriptionId}")
    public ResponseEntity<AtsAssessmentResponse> assessAts(
            @CallerId String userId, @PathVariable String jobDescriptionId) {
        return ResponseEntity.ok(toAtsResponse(assessmentService.assessAts(userId, jobDescriptionId)));
    }

    @GetMapping("/ats/{jobDescriptionId}")
    public ResponseEntity<AtsAssessmentResponse> getAts(
            @CallerId String userId, @PathVariable String jobDescriptionId) {
        return ResponseEntity.ok(toAtsResponse(assessmentService.requireExistingAts(userId, jobDescriptionId)));
    }

    private static AssessmentResponse toResponse(AssessmentService.Assessment assessment) {
        JdFitAssessment fit = assessment.jdFit();

        return new AssessmentResponse(
                fit.jdOptimizationId(),
                fit.jobDescriptionId(),
                fit.compatibilityScore(),
                fit.coverage(),
                fit.keywordMatch(),
                fit.seniorityMatch(),
                fit.recency(),
                fit.requirementMatches().stream().map(AssessmentController::toMatch).toList(),
                fit.unmetHardRequirements().stream().map(AssessmentController::toMatch).toList(),
                fit.matchedKeywords(),
                fit.missingKeywords(),
                fit.readinessBand(),
                fit.bandRule(),
                fit.recommendations().stream().map(AssessmentController::toRecommendation).toList(),
                fit.assessedAt());
    }

    private static AtsAssessmentResponse toAtsResponse(AssessmentService.AtsResult result) {
        AtsAssessment ats = result.ats();
        return new AtsAssessmentResponse(
                ats.jobDescriptionId(),
                ats.atsScore(),
                ats.checks().stream().map(AssessmentController::toCheck).toList(),
                ats.assessedAt());
    }

    private static AtsCheckResponse toCheck(AtsCheckResult check) {
        return new AtsCheckResponse(check.name(), check.label(), check.passRatio(), check.weight());
    }

    private static RequirementMatchResponse toMatch(RequirementMatchResult m) {
        return new RequirementMatchResponse(m.requirementId(), m.text(), m.type(), m.matchStrength(), m.evidenceIds());
    }

    private static RecommendationResponse toRecommendation(RecommendationItem r) {
        return new RecommendationResponse(r.type(), r.severity(), r.message(), r.relatedRequirementId());
    }
}
