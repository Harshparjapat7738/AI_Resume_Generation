package ai.careerforge.application.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * The versioned content/render boundary for a resume (ADR-036) — {@code application-service}'s
 * deterministically assembled output, not ai-service's direct output. Everything upstream of
 * this record (evidence selection, ai-service's grounded content fragments,
 * {@code application-service}'s own assembly and {@code DocumentEvidenceValidator}) is where
 * facts are decided and checked; everything downstream ({@code render-service}'s Thymeleaf →
 * strict-XHTML → Open HTML to PDF pipeline) is pure, dumb presentation of already-validated data
 * and never reasons about facts at all.
 *
 * <p>Not persisted, not exposed over any endpoint, and never rendered by this record itself —
 * this module only defines the shape (see the workstream this record was added under).
 *
 * @param schemaVersion semantic version ({@code MAJOR.MINOR.PATCH}) of this document-model
 *                       shape, so content and rendering can evolve independently;
 *                       {@code render-service} must reject a version it doesn't recognise,
 *                       never guess at an unfamiliar shape
 * @param header        the candidate's identity block — no evidenceId, no photo
 * @param summary       an optional professional-summary paragraph synthesised across evidence;
 *                       {@code null} when assembly chose not to include one — still a
 *                       {@link ContentLeaf} when present, so it still cites real evidence
 * @param sections      ordered resume sections, ATS-standard headings only; a section with
 *                       nothing to show is omitted here and recorded in
 *                       {@link GapReport#sectionsOmitted()} instead
 * @param renderHints   presentation-only knobs, kept separate from every content field
 * @param gapReport     deterministic coverage facts — never a score
 */
public record ResumeDocumentModel(
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$") String schemaVersion,
        @NotNull @Valid DocumentHeader header,
        @Valid ContentLeaf summary,
        @NotEmpty @Valid List<ResumeSection> sections,
        @NotNull @Valid RenderHints renderHints,
        @NotNull @Valid GapReport gapReport) {

    public ResumeDocumentModel {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
