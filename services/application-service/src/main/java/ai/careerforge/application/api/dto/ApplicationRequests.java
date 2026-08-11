package ai.careerforge.application.api.dto;

import ai.careerforge.application.domain.ApplicationStatus;
import ai.careerforge.application.domain.GenerationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ApplicationRequests {

    private ApplicationRequests() {
    }

    /** {@code resumeVersionId} is optional: an application can be created before generation
     *  runs (status stays {@code DRAFT}) or right after, in the same call as resume-service's
     *  result (status derives to {@code COMPLETED}/{@code PROCESSING} — see
     *  {@code Application.deriveStatus}). */
    public record CreateApplicationRequest(
            @NotBlank String jobDescriptionId,
            @NotNull GenerationType generationType,
            String templateId,
            String resumeVersionId) {
    }

    public record AttachResumeRequest(@NotBlank String resumeVersionId) {
    }

    public record UpdateStatusRequest(@NotNull ApplicationStatus status, String note) {
    }

    /** Records that one output of a {@code GenerationType.ALL} application failed to
     *  generate — see {@code Application.recordOutputFailure}. {@code reason} is a short,
     *  already-safe-to-display message (the caller's own caught error message, e.g.
     *  {@code ApiException.getMessage()} — never a stack trace). */
    public record RecordOutputFailureRequest(@NotBlank String reason) {
    }
}
