package ai.careerforge.render.template;

/** Which of ADR-036's two document-model shapes a template lays out — a resume's ATS-standard
 *  sections, or a cover letter's ordered paragraphs. The two are different files; a
 *  {@code RenderTemplate} identifier alone (e.g. {@code STANDARD}) is not a complete lookup
 *  key without this. */
public enum DocumentKind {
    RESUME,
    COVER_LETTER
}
