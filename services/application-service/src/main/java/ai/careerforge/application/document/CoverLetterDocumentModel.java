package ai.careerforge.application.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The versioned content/render boundary for a cover letter (ADR-036) — the same contract
 * {@link ResumeDocumentModel} is, shaped for a letter rather than a section-based document. A
 * cover letter has no ATS section headings to enforce (that constraint is a resume-parsing
 * concern; ATS systems don't structurally parse cover letters), so its natural ordered unit is
 * the paragraph, not the section/entry pair.
 *
 * @param schemaVersion  semantic version ({@code MAJOR.MINOR.PATCH}); see
 *                       {@link ResumeDocumentModel#schemaVersion()}
 * @param header         the candidate's identity block — no evidenceId, no photo
 * @param targetRole     the confirmed target role from the JD; allowed as context without an
 *                       evidence citation — it is the job's own fact, not a candidate claim
 *                       (mirrors {@code GroundingValidator}'s allowance for the same field in
 *                       email content); nullable
 * @param targetCompany  the confirmed target company, when known; same allowance; nullable
 * @param salutation     e.g. {@code "Dear Hiring Manager,"} — deterministic, never generated
 * @param paragraphs     ordered, grounded body paragraphs; every one a {@link ContentLeaf}
 * @param closing         e.g. {@code "Sincerely,"} — deterministic, never generated
 * @param signatureName  the candidate's own stated name, copied verbatim — never invented
 * @param renderHints    presentation-only knobs, kept separate from every content field
 * @param gapReport      deterministic coverage facts — never a score
 */
public record CoverLetterDocumentModel(
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$") String schemaVersion,
        @NotNull @Valid DocumentHeader header,
        @Size(max = 200) String targetRole,
        @Size(max = 200) String targetCompany,
        @NotBlank @Size(max = 100) String salutation,
        @NotEmpty @Valid List<ContentLeaf> paragraphs,
        @NotBlank @Size(max = 100) String closing,
        @NotBlank @Size(max = 200) String signatureName,
        @NotNull @Valid RenderHints renderHints,
        @NotNull @Valid GapReport gapReport) {

    public CoverLetterDocumentModel {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
}
