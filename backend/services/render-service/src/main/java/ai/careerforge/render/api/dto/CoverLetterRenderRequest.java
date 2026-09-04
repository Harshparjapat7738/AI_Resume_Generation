package ai.careerforge.render.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A request to render one cover letter — render-service's own copy of the
 * {@code CoverLetterDocumentModel} contract ADR-036 describes (ADR-006: no shared DTO module).
 * A cover letter has no ATS section headings to fill (that constraint is a resume-parsing
 * concern), so its ordered unit is the paragraph, not the section/entry pair.
 *
 * @param schemaVersion  semantic version ({@code MAJOR.MINOR.PATCH}); see
 *                       {@link ResumeRenderRequest#schemaVersion()}
 * @param template       which built-in Thymeleaf layout to fill
 * @param outputFormat   the file format to produce; {@code PDF} only today
 * @param header         the candidate's identity block — no evidenceId, no photo
 * @param targetRole     the confirmed target role, echoed from the JD; not evidence — the
 *                       job's own fact, not a candidate claim; nullable
 * @param targetCompany  the confirmed target company, when known; nullable
 * @param salutation     e.g. {@code "Dear Hiring Manager,"} — already decided, not generated here
 * @param paragraphs     ordered, already-grounded body paragraphs
 * @param closing        e.g. {@code "Sincerely,"}
 * @param signatureName  the candidate's own stated name, copied verbatim
 * @param renderHints    presentation-only knobs, kept separate from every content field
 */
public record CoverLetterRenderRequest(
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$") String schemaVersion,
        @NotNull RenderTemplate template,
        @NotNull OutputFormat outputFormat,
        @NotNull @Valid DocumentHeader header,
        @Size(max = 200) String targetRole,
        @Size(max = 200) String targetCompany,
        @NotBlank @Size(max = 100) String salutation,
        @NotEmpty @Valid List<ContentLeaf> paragraphs,
        @NotBlank @Size(max = 100) String closing,
        @NotBlank @Size(max = 200) String signatureName,
        @NotNull @Valid RenderHints renderHints) {

    public CoverLetterRenderRequest {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
}
