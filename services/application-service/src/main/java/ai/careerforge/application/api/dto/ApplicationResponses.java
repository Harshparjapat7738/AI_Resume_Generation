package ai.careerforge.application.api.dto;

import ai.careerforge.application.domain.ApplicationStatus;
import ai.careerforge.application.domain.GenerationType;
import java.time.Instant;
import java.util.List;

public final class ApplicationResponses {

    private ApplicationResponses() {
    }

    public record ApplicationResponse(
            String id,
            String jobDescriptionId,
            String jobTitle,
            String company,
            GenerationType generationType,
            String templateId,
            String resumeVersionId,
            String coverLetterVersionId,
            String emailId,
            boolean assessed,
            ApplicationStatus status,
            String failureCode,
            String resumeError,
            String coverLetterError,
            String emailError,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * Lightweight row for the history/dashboard list — no full content, but still enough to
     * render "Resume &#10003; | Cover Letter &#10007;" per-output status without an extra
     * {@code GET} per row (the dashboard needs this for {@code GenerationType.ALL} rows;
     * {@code null} in every one of these six fields is harmless for the other generation
     * types, which only ever populate the one field relevant to them).
     */
    public record ApplicationSummaryResponse(
            String id, String jobDescriptionId, String jobTitle, String company,
            GenerationType generationType, String templateId, ApplicationStatus status,
            String resumeVersionId, String coverLetterVersionId, String emailId,
            String resumeError, String coverLetterError, String emailError,
            Instant createdAt) {
    }

    public record StatusHistoryResponse(
            ApplicationStatus fromStatus, ApplicationStatus toStatus, String note, Instant changedAt) {
    }

    /** A model-generated, grounded paragraph kept for audit traceability — see
     *  {@code EmailContent} Javadoc (ARCHITECTURE_DECISIONS.md ADR-019). */
    public record HighlightResponse(String text, List<String> evidenceIds) {
    }

    public record EmailContentResponse(
            String id,
            String applicationId,
            String subject,
            String body,
            List<HighlightResponse> highlights,
            java.util.Map<String, Object> grounding,
            List<String> removedParagraphs,
            int version,
            Instant createdAt) {
    }

    /** Full cover-letter version — the model's grounded content plus its verification report,
     *  mirroring resume-service's {@code ResumeVersionResponse} shape (both store {@code
     *  content}/{@code grounding} as opaque, already-validated JSON). */
    public record CoverLetterVersionResponse(
            String id,
            String applicationId,
            String jobDescriptionId,
            String jobTitle,
            String company,
            int version,
            java.util.Map<String, Object> content,
            java.util.Map<String, Object> grounding,
            List<String> removedParagraphs,
            Instant createdAt) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
