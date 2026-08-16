package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.assessment.client.ClientDtos.ProfileDto;
import ai.careerforge.assessment.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.assessment.client.JdServiceClient;
import ai.careerforge.assessment.client.ProfileServiceClient;
import ai.careerforge.assessment.domain.JdFitAssessment;
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
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final JdServiceClient jdServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final JdFitScoringEngine jdFitScoringEngine;
    private final JdFitAssessmentRepository jdFitAssessments;

    public AssessmentService(JdServiceClient jdServiceClient, ProfileServiceClient profileServiceClient,
                             JdFitScoringEngine jdFitScoringEngine,
                             JdFitAssessmentRepository jdFitAssessments) {
        this.jdServiceClient = jdServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.jdFitScoringEngine = jdFitScoringEngine;
        this.jdFitAssessments = jdFitAssessments;
    }

    /**
     * ATS scoring was dropped with resume generation (ADR-033): every one of its checks read a
     * rendered/structured resume — section headings, bullet lengths, formatting — and there is
     * no resume to read any more. What survives is JD fit, which was always computed from the
     * JD, the profile and the requirement/evidence mapping.
     */
    public record Assessment(JdFitAssessment jdFit) {
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
}
