package ai.careerforge.render.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The outcome of one render attempt. Exactly one of {@code document}/{@code errors} is
 * populated, matching {@code status} — enforced by {@link #isConsistent()}, not left to
 * convention.
 *
 * @param status   the render outcome
 * @param document artifact metadata; present only when {@code status == SUCCEEDED}
 * @param errors   why rendering failed; present (non-empty) only when {@code status == FAILED}
 */
public record RenderResponse(
        @NotNull RenderStatus status,
        @Valid DocumentMetadata document,
        @Valid List<RenderError> errors) {

    public RenderResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static RenderResponse succeeded(DocumentMetadata document) {
        return new RenderResponse(RenderStatus.SUCCEEDED, document, List.of());
    }

    public static RenderResponse failed(List<RenderError> errors) {
        return new RenderResponse(RenderStatus.FAILED, null, errors);
    }

    /** Cross-field shape rule: a {@code SUCCEEDED} response is never left without document
     *  metadata, and a {@code FAILED} response is never left without a reason — never silently
     *  ambiguous about which happened. */
    @JsonIgnore
    @AssertTrue(message = "a SUCCEEDED response must carry document metadata and no errors; "
            + "a FAILED response must carry at least one error and no document metadata")
    public boolean isConsistent() {
        if (status == null) {
            return true; // @NotNull already reports the real problem
        }
        return switch (status) {
            case SUCCEEDED -> document != null && errors.isEmpty();
            case FAILED -> document == null && !errors.isEmpty();
        };
    }
}
