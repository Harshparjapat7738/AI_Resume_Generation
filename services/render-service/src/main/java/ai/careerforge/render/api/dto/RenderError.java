package ai.careerforge.render.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One reason a render attempt failed — a fact to act on, never a stack trace, a file path, or
 * any other internal detail (platform-common's {@code ApiError} convention, applied here too).
 *
 * @param code    a stable, machine-checkable reason, e.g. {@code "UNSUPPORTED_SCHEMA_VERSION"},
 *                {@code "TEMPLATE_NOT_FOUND"}
 * @param message a human-readable explanation, safe to show a caller
 */
public record RenderError(
        @NotBlank String code,
        @NotBlank String message) {
}
