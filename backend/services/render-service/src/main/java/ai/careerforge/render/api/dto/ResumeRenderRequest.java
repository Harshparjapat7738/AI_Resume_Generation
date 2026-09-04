package ai.careerforge.render.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * A request to render one resume — the {@code ResumeDocumentModel} contract ADR-036 describes,
 * as render-service's own copy of it (ADR-006: no shared DTO module; this is deliberately not
 * an import of application-service's record of the same shape).
 *
 * <p>The document content ({@code header}/{@code summary}/{@code sections}) is exactly what
 * application-service already assembled and validated (evidence checked by
 * {@code GroundingValidator} and {@code DocumentEvidenceValidator} upstream) — render-service
 * never re-derives or second-guesses it, only lays it out. {@code template} and
 * {@code outputFormat} are render-service's own concern: which built-in layout to fill, and
 * what file format to produce.
 *
 * @param schemaVersion semantic version ({@code MAJOR.MINOR.PATCH}) of the document-model
 *                       shape this request carries; render-service must reject a version it
 *                       doesn't recognise, never guess at an unfamiliar shape
 * @param template      which built-in Thymeleaf layout to fill
 * @param outputFormat  the file format to produce; {@code PDF} only today
 * @param header        the candidate's identity block — no evidenceId, no photo
 * @param summary       an optional professional-summary paragraph; {@code null} when assembly
 *                       chose not to include one
 * @param sections      ordered resume sections, ATS-standard headings only
 * @param renderHints   presentation-only knobs, kept separate from every content field
 */
public record ResumeRenderRequest(
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$") String schemaVersion,
        @NotNull RenderTemplate template,
        @NotNull OutputFormat outputFormat,
        @NotNull @Valid DocumentHeader header,
        @Valid ContentLeaf summary,
        @NotEmpty @Valid List<ResumeSection> sections,
        @NotNull @Valid RenderHints renderHints) {

    public ResumeRenderRequest {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
