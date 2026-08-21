package ai.careerforge.application.api;

import ai.careerforge.application.api.dto.ApplicationRequests.AttachResumeRequest;
import ai.careerforge.application.api.dto.ApplicationRequests.CreateApplicationRequest;
import ai.careerforge.application.api.dto.ApplicationRequests.RecordOutputFailureRequest;
import ai.careerforge.application.api.dto.ApplicationRequests.UpdateStatusRequest;
import ai.careerforge.application.api.dto.ApplicationResponses.ApplicationResponse;
import ai.careerforge.application.api.dto.ApplicationResponses.ApplicationSummaryResponse;
import ai.careerforge.application.api.dto.ApplicationResponses.EmailContentResponse;
import ai.careerforge.application.api.dto.ApplicationResponses.HighlightResponse;
import ai.careerforge.application.api.dto.ApplicationResponses.PageResponse;
import ai.careerforge.application.api.dto.ApplicationResponses.StatusHistoryResponse;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.ApplicationStatus;
import ai.careerforge.application.domain.ApplicationStatusHistory;
import ai.careerforge.application.domain.EmailContent;
import ai.careerforge.application.service.ApplicationService;
import ai.careerforge.application.service.EmailGenerationService;
import ai.careerforge.application.service.ResumeRenderService;
import ai.careerforge.common.security.CallerId;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * See docs/API_CATALOG.md &sect;3 (Milestone 8 — application-service) and
 * ARCHITECTURE_DECISIONS.md ADR-017 / ADR-019.
 *
 * <p>Gmail-draft generation ({@code POST .../gmail-draft}) is documented for this milestone
 * but intentionally not implemented yet. {@code EMAIL_ONLY} is implemented (ADR-019) and
 * {@code COVER_LETTER_ONLY} is implemented (ADR-020) — {@code ALL} remains a real, storable
 * {@link ai.careerforge.application.domain.GenerationType} with no combined pipeline behind
 * it yet.
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final EmailGenerationService emailGenerationService;
    private final ResumeRenderService resumeRenderService;

    public ApplicationController(ApplicationService applicationService,
                                 EmailGenerationService emailGenerationService,
                                 ResumeRenderService resumeRenderService) {
        this.applicationService = applicationService;
        this.emailGenerationService = emailGenerationService;
        this.resumeRenderService = resumeRenderService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @CallerId String userId, @Valid @RequestBody CreateApplicationRequest request) {
        Application application = applicationService.create(
                userId, request.jobDescriptionId(), request.generationType(),
                request.templateId(), request.resumeVersionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(application));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApplicationResponse> attachResume(
            @CallerId String userId, @PathVariable String id, @Valid @RequestBody AttachResumeRequest request) {
        Application application = applicationService.attachResume(userId, id, request.resumeVersionId());
        return ResponseEntity.ok(toResponse(application));
    }

    /**
     * Renders this application's resume as a PDF, right now, connecting the JD's optimization
     * (jd-service, ADR-033) to render-service's rendering pipeline (ADR-036). Synchronous, and
     * not persisted — render-service does not yet store what it renders, so calling this again
     * simply renders again from whatever the JD optimization and profile currently say; there
     * is no version to attach the way {@link #attachResume} attaches an externally-generated one.
     *
     * <p>Deliberately no {@code produces = APPLICATION_PDF_VALUE} on the mapping itself — every
     * frontend call through {@code apiClient.ts} sends a blanket {@code Accept: application/json}
     * (it doesn't know ahead of time which endpoints return binary), and restricting
     * {@code produces} here would make Spring's content negotiation reject that as
     * {@code HttpMediaTypeNotAcceptableException} before this method ever runs — exactly the
     * failure mode profile-service's own {@code TemplateController.download} avoids the same
     * way. The actual response content type is still set correctly below, via the
     * {@code ResponseEntity} itself.
     */
    @PostMapping("/{id}/resume/render")
    public ResponseEntity<byte[]> renderResume(@CallerId String userId, @PathVariable String id) {
        byte[] pdf = resumeRenderService.renderResumePdf(userId, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(applicationService.requireOwned(userId, id)));
    }

    /**
     * Records that one of an application's outputs failed to generate. Outputs are
     * each generated independently and a failure in one must not block, hide, or be confused
     * with the others. {@code output} is one of {@code resume}, {@code coverLetter}, {@code
     * email}. Persisting this (rather than only surfacing the failed call's own error to the
     * caller) is what lets a page refresh still show exactly which output failed and why —
     * the same reason {@code resumeVersionId}/{@code coverLetterVersionId}/{@code emailId}
     * already survive a refresh.
     */
    @PostMapping("/{id}/outputs/{output}/failed")
    public ResponseEntity<ApplicationResponse> recordOutputFailure(
            @CallerId String userId, @PathVariable String id, @PathVariable String output,
            @Valid @RequestBody RecordOutputFailureRequest request) {
        Application application = applicationService.recordOutputFailure(userId, id, output, request.reason());
        return ResponseEntity.ok(toResponse(application));
    }

    /** Paged history for the /dashboard screen; optionally filtered by lifecycle status. */
    @GetMapping
    public ResponseEntity<PageResponse<ApplicationSummaryResponse>> list(
            @CallerId String userId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        Page<Application> result = applicationService.list(userId, status, PageRequest.of(page, cappedSize));
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent().stream().map(ApplicationController::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @CallerId String userId, @PathVariable String id, @Valid @RequestBody UpdateStatusRequest request) {
        Application application = applicationService.updateStatus(userId, id, request.status(), request.note());
        return ResponseEntity.ok(toResponse(application));
    }

    @GetMapping("/{id}/status-history")
    public ResponseEntity<java.util.List<StatusHistoryResponse>> statusHistory(
            @CallerId String userId, @PathVariable String id) {
        java.util.List<ApplicationStatusHistory> rows = applicationService.statusHistory(userId, id);
        return ResponseEntity.ok(rows.stream()
                .map(h -> new StatusHistoryResponse(h.fromStatus(), h.toStatus(), h.note(), h.changedAt()))
                .toList());
    }

    /** Generates a grounded application email (subject + body) for an {@code EMAIL_ONLY}
     *  application — see ARCHITECTURE_DECISIONS.md ADR-019. Calling this again regenerates:
     *  a new version is persisted and the application repoints at it, mirroring resume-service's
     *  regenerate-by-calling-again pattern. */
    @PostMapping("/{id}/email")
    public ResponseEntity<EmailContentResponse> generateEmail(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toEmailResponse(emailGenerationService.generate(userId, id)));
    }

    /** Latest generated email for this application. {@code 404} if none has been generated yet. */
    @GetMapping("/{id}/email")
    public ResponseEntity<EmailContentResponse> getEmail(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toEmailResponse(emailGenerationService.requireLatest(userId, id)));
    }

    private static ApplicationSummaryResponse toSummary(Application application) {
        return new ApplicationSummaryResponse(
                application.id(), application.jobDescriptionId(), application.jobTitle(), application.company(),
                application.generationType(), application.templateId(), application.status(),
                application.resumeVersionId(), application.coverLetterVersionId(), application.emailId(),
                application.resumeError(), application.coverLetterError(), application.emailError(),
                application.createdAt());
    }

    private static ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.id(), application.jobDescriptionId(), application.jobTitle(), application.company(),
                application.generationType(), application.templateId(), application.resumeVersionId(),
                application.coverLetterVersionId(), application.emailId(), application.assessed(),
                application.status(), application.failureCode(),
                application.resumeError(), application.coverLetterError(), application.emailError(),
                application.createdAt(), application.updatedAt());
    }

    @SuppressWarnings("unchecked")
    private static EmailContentResponse toEmailResponse(EmailContent email) {
        java.util.List<HighlightResponse> highlights = email.highlights() == null ? java.util.List.of()
                : email.highlights().stream()
                        .map(h -> new HighlightResponse(
                                String.valueOf(h.get("text")),
                                h.get("evidenceIds") instanceof java.util.List<?> ids
                                        ? ids.stream().map(String::valueOf).toList()
                                        : java.util.List.<String>of()))
                        .toList();
        return new EmailContentResponse(
                email.id(), email.applicationId(), email.subject(), email.body(), highlights,
                email.groundingReport(), email.removedParagraphs(), email.version(), email.createdAt());
    }
}
