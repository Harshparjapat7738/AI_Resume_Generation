package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.assessment.client.ClientDtos.ProfileDto;
import ai.careerforge.assessment.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.assessment.client.JdServiceClient;
import ai.careerforge.assessment.client.ProfileServiceClient;
import ai.careerforge.assessment.client.ResumeServiceClient;
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
 * Orchestrates ATS + JD-fit scoring: pulls the already-generated resume (resume-service),
 * its JD analysis (jd-service) and the candidate's profile (profile-service), runs both
 * deterministic engines, and persists the result. Nothing here calls an LLM — see
 * {@link AtsScoringEngine} and {@link JdFitScoringEngine}.
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final ResumeServiceClient resumeServiceClient;
    private final JdServiceClient jdServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final AtsScoringEngine atsScoringEngine;
    private final JdFitScoringEngine jdFitScoringEngine;
    private final AtsAssessmentRepository atsAssessments;
    private final JdFitAssessmentRepository jdFitAssessments;

    public AssessmentService(ResumeServiceClient resumeServiceClient, JdServiceClient jdServiceClient,
                             ProfileServiceClient profileServiceClient, AtsScoringEngine atsScoringEngine,
                             JdFitScoringEngine jdFitScoringEngine, AtsAssessmentRepository atsAssessments,
                             JdFitAssessmentRepository jdFitAssessments) {
        this.resumeServiceClient = resumeServiceClient;
        this.jdServiceClient = jdServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.atsScoringEngine = atsScoringEngine;
        this.jdFitScoringEngine = jdFitScoringEngine;
        this.atsAssessments = atsAssessments;
        this.jdFitAssessments = jdFitAssessments;
    }

    public record Assessment(AtsAssessment ats, JdFitAssessment jdFit) {
    }

    /** Computes and persists on first call; returns the cached result on every call after. */
    public Assessment assess(String userId, String resumeVersionId) {
        var existingAts = atsAssessments.findByResumeVersionIdAndUserId(resumeVersionId, userId);
        var existingFit = jdFitAssessments.findByResumeVersionIdAndUserId(resumeVersionId, userId);
        if (existingAts.isPresent() && existingFit.isPresent()) {
            return new Assessment(existingAts.get(), existingFit.get());
        }

        ResumeVersionDto resume = fetchResume(resumeVersionId);
        JdAnalysisDto jdAnalysis = fetchJdAnalysis(resume.jobDescriptionId());
        ProfileDto profile = fetchProfile();
        List<ai.careerforge.assessment.client.ClientDtos.ExperienceDto> experiences =
                profile.experiences() == null ? List.of() : profile.experiences();

        var checks = atsScoringEngine.score(resume, profile.personalInformation(), experiences, jdAnalysis.keywords());
        AtsAssessment ats = atsAssessments.save(
                new AtsAssessment(resumeVersionId, userId, checks, AtsScoringEngine.ENGINE_VERSION));

        JdFitScoringEngine.Result fitResult = jdFitScoringEngine.score(resume, jdAnalysis, experiences);
        JdFitAssessment fit = jdFitAssessments.save(new JdFitAssessment(
                resumeVersionId, userId, resume.jobDescriptionId(), fitResult.compatibilityScore(),
                fitResult.coverage(), fitResult.keywordMatch(), fitResult.seniorityMatch(), fitResult.recency(),
                fitResult.requirementMatches(), fitResult.unmetHardRequirements(), fitResult.matchedKeywords(),
                fitResult.missingKeywords(), fitResult.readinessBand(), fitResult.bandRule(),
                fitResult.recommendations()));

        return new Assessment(ats, fit);
    }

    public Assessment requireExisting(String userId, String resumeVersionId) {
        AtsAssessment ats = atsAssessments.findByResumeVersionIdAndUserId(resumeVersionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        JdFitAssessment fit = jdFitAssessments.findByResumeVersionIdAndUserId(resumeVersionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return new Assessment(ats, fit);
    }

    private ResumeVersionDto fetchResume(String resumeVersionId) {
        try {
            return resumeServiceClient.getResume(resumeVersionId);
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("resume-service call failed for resumeVersionId={}: {}", resumeVersionId, ex.getMessage());
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
