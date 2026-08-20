package ai.careerforge.render.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Facts about a successfully rendered artifact — never a quality judgement about it (ADR-036).
 * {@code documentId} is an opaque reference (an object key or job id in render-service's own
 * store, ADR-036's Impact section); it is not the PDF bytes themselves, and render-service
 * exposes no business endpoint yet to fetch them by this id (a later step's concern).
 *
 * @param documentId opaque reference to the rendered artifact
 * @param format     the format actually produced
 * @param sizeBytes  artifact size; must stay under the platform's 2MB hard limit
 * @param pageCount  pages actually rendered
 * @param renderedAt when rendering completed
 */
public record DocumentMetadata(
        @NotBlank String documentId,
        @NotNull OutputFormat format,
        @Min(0) long sizeBytes,
        @Min(1) int pageCount,
        @NotNull Instant renderedAt) {
}
