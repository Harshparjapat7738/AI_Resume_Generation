package ai.careerforge.document.domain;

/** docs/DATABASE.md &sect;3 ({@code rendered_documents.format}). Only {@code PDF} is
 *  produced today — DOCX rendering is left for a later slice; the docx4j dependency stays
 *  declared for that but is unused. */
public enum DocumentFormat {
    PDF
}
