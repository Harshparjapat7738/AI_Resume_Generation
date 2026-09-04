package ai.careerforge.render.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of one render attempt. Exactly one of {@code document}/{@code pdfBytes} pair vs.
 * {@code errors} is populated, matching {@code status} — enforced by {@link #isConsistent()},
 * not left to convention.
 *
 * <p>{@code pdfBytes} travels inline in this response rather than by reference: render-service
 * persists nothing (this workstream's rendering steps are explicitly PDF-generation-without-
 * persistence), so there is no object-store key yet for a caller to fetch bytes by later — the
 * bytes themselves are the only way this response can actually hand over what was rendered.
 * {@code document.documentId()} is therefore a synthetic, in-memory-only identifier today, not
 * yet a real object-store key (see {@code DocumentMetadata}); a future persistence step can
 * replace this inline-bytes shape with a fetch-by-id one without touching anything upstream of
 * this DTO.
 *
 * @param status   the render outcome
 * @param document artifact metadata; present only when {@code status == SUCCEEDED}
 * @param pdfBytes the rendered PDF; present (non-empty) only when {@code status == SUCCEEDED}
 * @param errors   why rendering failed; present (non-empty) only when {@code status == FAILED}
 */
public record RenderResponse(
        @NotNull RenderStatus status,
        @Valid DocumentMetadata document,
        byte[] pdfBytes,
        @Valid List<RenderError> errors) {

    public RenderResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        pdfBytes = pdfBytes == null ? null : pdfBytes.clone();
    }

    /** Defensive copy — mutating the returned array never affects this instance. */
    @Override
    public byte[] pdfBytes() {
        return pdfBytes == null ? null : pdfBytes.clone();
    }

    public static RenderResponse succeeded(DocumentMetadata document, byte[] pdfBytes) {
        return new RenderResponse(RenderStatus.SUCCEEDED, document, pdfBytes, List.of());
    }

    public static RenderResponse failed(List<RenderError> errors) {
        return new RenderResponse(RenderStatus.FAILED, null, null, errors);
    }

    /** Cross-field shape rule: a {@code SUCCEEDED} response is never left without document
     *  metadata and the actual bytes, and a {@code FAILED} response is never left without a
     *  reason — never silently ambiguous about which happened. */
    @JsonIgnore
    @AssertTrue(message = "a SUCCEEDED response must carry document metadata, non-empty pdfBytes "
            + "and no errors; a FAILED response must carry at least one error and neither "
            + "document metadata nor pdfBytes")
    public boolean isConsistent() {
        if (status == null) {
            return true; // @NotNull already reports the real problem
        }
        return switch (status) {
            case SUCCEEDED -> document != null && pdfBytes != null && pdfBytes.length > 0 && errors.isEmpty();
            case FAILED -> document == null && pdfBytes == null && !errors.isEmpty();
        };
    }

    // byte[] has reference-equality equals()/hashCode() by default, which the record's
    // generated implementations would otherwise inherit unchanged — see RenderedPdf's own
    // Javadoc for the same reasoning.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RenderResponse other)) {
            return false;
        }
        return status == other.status && Objects.equals(document, other.document)
                && Arrays.equals(pdfBytes, other.pdfBytes) && Objects.equals(errors, other.errors);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(status, document, errors);
        return 31 * result + Arrays.hashCode(pdfBytes);
    }
}
