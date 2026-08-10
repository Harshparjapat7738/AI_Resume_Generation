package ai.careerforge.application.service;

import ai.careerforge.application.client.AssessmentServiceClient;
import ai.careerforge.application.client.ClientDtos.JobDescriptionDto;
import ai.careerforge.application.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ResumeServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.ApplicationStatus;
import ai.careerforge.application.domain.ApplicationStatusHistory;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.ApplicationRepository;
import ai.careerforge.application.repository.ApplicationStatusHistoryRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import feign.FeignException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the {@link Application} aggregate: creation (verifying and denormalising from
 * jd-service), attaching a generated resume (verifying against resume-service and, best
 * effort, assessment-service), and lifecycle transitions. See ARCHITECTURE_DECISIONS.md
 * ADR-017.
 *
 * <p>This service never calls ai-service and never drives the generation pipeline itself —
 * that remains resume-service's job (ADR-013), untouched. It only records the outcome by
 * reference, matching docs/API_CATALOG.md's Milestone 8 contract: "save an application
 * version with JD, resume, scores and template".
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    /** Legal {@code fromStatus -> toStatus} transitions for {@code PATCH .../status}.
     *  {@code COMPLETED} is terminal; every other state can still move. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
            ApplicationStatus.DRAFT, EnumSet.of(ApplicationStatus.PROCESSING, ApplicationStatus.FAILED),
            ApplicationStatus.PROCESSING, EnumSet.of(ApplicationStatus.COMPLETED, ApplicationStatus.FAILED),
            ApplicationStatus.FAILED, EnumSet.of(ApplicationStatus.PROCESSING, ApplicationStatus.DRAFT),
            ApplicationStatus.COMPLETED, EnumSet.noneOf(ApplicationStatus.class)));

    private final JdServiceClient jdServiceClient;
    private final ResumeServiceClient resumeServiceClient;
    private final AssessmentServiceClient assessmentServiceClient;
    private final ApplicationRepository applications;
    private final ApplicationStatusHistoryRepository history;

    public ApplicationService(JdServiceClient jdServiceClient, ResumeServiceClient resumeServiceClient,
                              AssessmentServiceClient assessmentServiceClient, ApplicationRepository applications,
                              ApplicationStatusHistoryRepository history) {
        this.jdServiceClient = jdServiceClient;
        this.resumeServiceClient = resumeServiceClient;
        this.assessmentServiceClient = assessmentServiceClient;
        this.applications = applications;
        this.history = history;
    }

    /** Creates an application record for the given job description. If a
     *  {@code resumeVersionId} the caller already generated (via resume-service, ADR-013) is
     *  supplied, it is verified and attached in the same call — status is derived, never
     *  supplied by the caller. */
    public Application create(String userId, String jobDescriptionId, GenerationType generationType,
                              String templateId, String resumeVersionId) {
        JobDescriptionDto jd = fetchJobDescription(userId, jobDescriptionId);
        if (templateId != null && !templateId.isBlank()) {
            fetchTemplate(userId, templateId);
        }
        Application application = new Application(userId, jobDescriptionId, jd.title(), jd.company(),
                generationType, templateId);

        if (resumeVersionId != null && !resumeVersionId.isBlank()) {
            attachResume(application, userId, resumeVersionId);
        }

        return applications.save(application);
    }

    /** Attaches (or replaces) the generated resume reference on an existing application. */
    public Application attachResume(String userId, String id, String resumeVersionId) {
        Application application = requireOwned(userId, id);
        attachResume(application, userId, resumeVersionId);
        return applications.save(application);
    }

    private void attachResume(Application application, String userId, String resumeVersionId) {
        ResumeVersionDto resume = fetchResume(resumeVersionId);
        if (!application.jobDescriptionId().equals(resume.jobDescriptionId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This resume was not generated for this application's job description.");
        }
        boolean assessed = fetchAssessed(userId, resumeVersionId);
        application.attachResume(resumeVersionId, assessed);
    }

    public Application requireOwned(String userId, String id) {
        return applications.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /** Attaches (or replaces, on regenerate) the generated email reference on an existing
     *  application and recomputes status. The email content itself is generated and persisted
     *  by {@code EmailGenerationService} (ARCHITECTURE_DECISIONS.md ADR-019); this only records
     *  the resulting reference on the aggregate, the same way {@link #attachResume} does. */
    public Application attachEmail(String userId, String id, String emailId) {
        Application application = requireOwned(userId, id);
        application.attachEmail(emailId);
        return applications.save(application);
    }

    /** Attaches (or replaces, on regenerate) the generated cover-letter reference on an
     *  existing application and recomputes status. The letter itself is generated and
     *  persisted by {@link CoverLetterGenerationService}; this only records the resulting
     *  reference on the aggregate, the same way {@link #attachEmail} does. */
    public Application attachCoverLetter(String userId, String id, String coverLetterVersionId) {
        Application application = requireOwned(userId, id);
        application.attachCoverLetter(coverLetterVersionId);
        return applications.save(application);
    }

    public Page<Application> list(String userId, ApplicationStatus status, Pageable pageable) {
        return status == null
                ? applications.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : applications.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
    }

    public Application updateStatus(String userId, String id, ApplicationStatus toStatus, String note) {
        Application application = requireOwned(userId, id);
        ApplicationStatus fromStatus = application.status();

        Set<ApplicationStatus> allowed = ALLOWED_TRANSITIONS.get(fromStatus);
        if (allowed == null || !allowed.contains(toStatus)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot move an application from " + fromStatus + " to " + toStatus + ".");
        }

        if (toStatus == ApplicationStatus.FAILED) {
            application.fail(note);
        } else {
            application.transitionTo(toStatus);
        }
        application = applications.save(application);

        history.save(new ApplicationStatusHistory(application.id(), userId, fromStatus, toStatus, note));
        return application;
    }

    public java.util.List<ApplicationStatusHistory> statusHistory(String userId, String id) {
        requireOwned(userId, id);
        return history.findByApplicationIdAndUserIdOrderByChangedAtDesc(id, userId);
    }

    private JobDescriptionDto fetchJobDescription(String userId, String jobDescriptionId) {
        try {
            return jdServiceClient.get(jobDescriptionId);
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("jd-service call failed for jobDescriptionId={}: {}", jobDescriptionId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private void fetchTemplate(String userId, String templateId) {
        try {
            resumeServiceClient.getTemplate(templateId);
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("resume-service template lookup failed for templateId={}: {}", templateId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
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

    /** Best-effort: a missing or unreachable assessment never blocks attaching the resume —
     *  mirrors the frontend's own non-fatal assessment call in {@code ProcessingPage}. */
    private boolean fetchAssessed(String userId, String resumeVersionId) {
        try {
            return assessmentServiceClient.get(resumeVersionId) != null;
        } catch (FeignException ex) {
            log.info("No assessment found yet for resumeVersionId={}: {}", resumeVersionId, ex.getMessage());
            return false;
        }
    }
}
