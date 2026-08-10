package ai.careerforge.assessment.api;

import ai.careerforge.assessment.api.dto.AssessmentResponses.AssessmentResponse;
import ai.careerforge.assessment.api.dto.AssessmentResponses.CheckResponse;
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
 * the blueprint's ten-check ATS engine — see ARCHITECTURE_DECISIONS.md ADR-014.
 */
@RestController
@RequestMapping("/api/assessment/resume-versions")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** Computes on first call; idempotent — returns the cached result thereafter. */
    @PostMapping("/{resumeVersionId}")
    public ResponseEntity<AssessmentResponse> assess(@CallerId String userId, @PathVariable String resumeVersionId) {
        return ResponseEntity.ok(toResponse(assessmentService.assess(userId, resumeVersionId)));
    }

    @GetMapping("/{resumeVersionId}")
    public ResponseEntity<AssessmentResponse> get(@CallerId String userId, @PathVariable String resumeVersionId) {
        return ResponseEntity.ok(toResponse(assessmentService.requireExisting(userId, resumeVersionId)));
    }

    private static AssessmentResponse toResponse(AssessmentService.Assessment assessment) {
        AtsAssessment ats = assessment.ats();
        JdFitAssessment fit = assessment.jdFit();

        return new AssessmentResponse(
                ats.resumeVersionId(),
                ats.totalScore(),
                ats.checks().stream().map(AssessmentController::toCheck).toList(),
                ats.engineVersion(),
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

    private static CheckResponse toCheck(AtsCheckResult c) {
        return new CheckResponse(c.checkId(), c.label(), c.weight(), c.passRatio(), c.detail(), c.earned());
    }

    private static RequirementMatchResponse toMatch(RequirementMatchResult m) {
        return new RequirementMatchResponse(m.requirementId(), m.text(), m.type(), m.matchStrength(), m.evidenceIds());
    }

    private static RecommendationResponse toRecommendation(RecommendationItem r) {
        return new RecommendationResponse(r.type(), r.severity(), r.message(), r.relatedRequirementId());
    }
}
