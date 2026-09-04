package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.EvidenceItem;
import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.assessment.client.ClientDtos.ProfileDto;
import ai.careerforge.assessment.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.assessment.client.JdServiceClient;
import ai.careerforge.assessment.client.ProfileServiceClient;
import ai.careerforge.assessment.domain.AtsAssessment;
import ai.careerforge.assessment.domain.JdFitAssessment;
import ai.careerforge.assessment.repository.AtsAssessmentRepository;
import ai.careerforge.assessment.repository.JdFitAssessmentRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import feign.FeignException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates JD-fit scoring: pulls the persisted JD optimization and its analysis
 * (jd-service) plus the candidate's profile (profile-service), runs the deterministic engine,
 * and persists the result. Nothing here calls an LLM — see {@link JdFitScoringEngine}.
 *
 * <p>Keyed on the optimization, not a resume version (ADR-033). Scoring is still entirely
 * deterministic Java; only its input moved.
 *
 * <p>Since ADR-040 this also orchestrates the revived ATS *structural* score the same way —
 * see {@link AtsScoringEngine} — computed from the same pre-render, cited-evidence content
 * application-service's {@code ResumeRenderService} assembles, never a rendered document, so it
 * is available whether or not that later render call succeeds.
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final JdServiceClient jdServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final JdFitScoringEngine jdFitScoringEngine;
    private final AtsScoringEngine atsScoringEngine;
    private final JdFitAssessmentRepository jdFitAssessments;
    private final AtsAssessmentRepository atsAssessments;

    public AssessmentService(JdServiceClient jdServiceClient, ProfileServiceClient profileServiceClient,
                             JdFitScoringEngine jdFitScoringEngine, AtsScoringEngine atsScoringEngine,
                             JdFitAssessmentRepository jdFitAssessments, AtsAssessmentRepository atsAssessments) {
        this.jdServiceClient = jdServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.jdFitScoringEngine = jdFitScoringEngine;
        this.atsScoringEngine = atsScoringEngine;
        this.jdFitAssessments = jdFitAssessments;
        this.atsAssessments = atsAssessments;
    }

    /**
     * JD-fit result. ATS *structural* scoring is a separate result — see {@link #assessAts} —
     * kept apart because it has a different, narrower input (pre-render evidence content only,
     * no JD analysis needed) and a different revival history (ADR-033 deferred it, ADR-040
     * revives it scoped to what never depends on a render step).
     */
    public record Assessment(JdFitAssessment jdFit) {
    }

    public record AtsResult(AtsAssessment ats) {
    }

    /** Computes and persists on first call; returns the cached result on every call after. */
    public Assessment assess(String userId, String jobDescriptionId) {
        JdOptimizationDto optimization = fetchOptimization(jobDescriptionId);
        var existingFit = jdFitAssessments.findByJdOptimizationIdAndUserId(optimization.id(), userId);
        if (existingFit.isPresent()) {
            return new Assessment(existingFit.get());
        }

        JdAnalysisDto jdAnalysis = fetchJdAnalysis(jobDescriptionId);
        ProfileDto profile = fetchProfile();
        List<ai.careerforge.assessment.client.ClientDtos.ExperienceDto> experiences =
                profile.experiences() == null ? List.of() : profile.experiences();

        JdFitScoringEngine.Result fitResult = jdFitScoringEngine.score(optimization, jdAnalysis, experiences);
        JdFitAssessment fit = jdFitAssessments.save(new JdFitAssessment(
                optimization.id(), userId, jobDescriptionId, fitResult.compatibilityScore(),
                fitResult.coverage(), fitResult.keywordMatch(), fitResult.seniorityMatch(), fitResult.recency(),
                fitResult.requirementMatches(), fitResult.unmetHardRequirements(), fitResult.matchedKeywords(),
                fitResult.missingKeywords(), fitResult.readinessBand(), fitResult.bandRule(),
                fitResult.recommendations()));

        return new Assessment(fit);
    }

    public Assessment requireExisting(String userId, String jobDescriptionId) {
        JdOptimizationDto optimization = fetchOptimization(jobDescriptionId);
        JdFitAssessment fit = jdFitAssessments.findByJdOptimizationIdAndUserId(optimization.id(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return new Assessment(fit);
    }

    /** Computes and persists the ATS structural score on first call; returns the cached result
     *  on every call after (ADR-040) — same idempotent shape as {@link #assess}. */
    public AtsResult assessAts(String userId, String jobDescriptionId) {
        JdOptimizationDto optimization = fetchOptimization(jobDescriptionId);
        var existingAts = atsAssessments.findByJdOptimizationIdAndUserId(optimization.id(), userId);
        if (existingAts.isPresent()) {
            return new AtsResult(existingAts.get());
        }

        ProfileDto profile = fetchProfile();
        List<EvidenceItem> evidence = fetchEvidence();

        AtsScoringEngine.Result atsResult = atsScoringEngine.score(optimization, profile.personalInformation(), evidence);
        AtsAssessment ats = atsAssessments.save(new AtsAssessment(
                optimization.id(), userId, jobDescriptionId, atsResult.atsScore(), atsResult.checks()));

        return new AtsResult(ats);
    }

    public AtsResult requireExistingAts(String userId, String jobDescriptionId) {
        JdOptimizationDto optimization = fetchOptimization(jobDescriptionId);
        AtsAssessment ats = atsAssessments.findByJdOptimizationIdAndUserId(optimization.id(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return new AtsResult(ats);
    }

    private JdOptimizationDto fetchOptimization(String jobDescriptionId) {
        try {
            return jdServiceClient.getOptimization(jobDescriptionId);
        } catch (FeignException.NotFound ex) {
            // No optimization (or not this caller's) — indistinguishable by design (ADR-007).
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("jd-service optimization call failed for jobDescriptionId={}: {}",
                    jobDescriptionId, ex.status());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }


    private JdAnalysisDto fetchJdAnalysis(String jobDescriptionId) {
        try {
            return jdServiceClient.getAnalysis(jobDescriptionId);
        } catch (FeignException ex) {
            log.warn("jd-service call failed for jobDescriptionId={}: {}", jobDescriptionId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private ProfileDto fetchProfile() {
        try {
            return profileServiceClient.getProfile();
        } catch (FeignException ex) {
            log.warn("profile-service call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private List<EvidenceItem> fetchEvidence() {
        try {
            return profileServiceClient.getEvidence();
        } catch (FeignException ex) {
            log.warn("profile-service evidence call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }
}
