package ai.careerforge.document.domain;

/** docs/DATABASE.md &sect;3 ({@code rendered_documents.format}). {@code PDF} is the built-in
 *  template pipeline's output (openhtmltopdf). {@code DOCX} is produced by the custom-template
 *  mail-merge pipeline (see {@code docx} package) — the only way to preserve a user-uploaded
 *  template's exact layout/formatting is to keep it in its native DOCX form rather than
 *  converting through HTML/PDF. */
public enum DocumentFormat {
    PDF,
    DOCX
}
