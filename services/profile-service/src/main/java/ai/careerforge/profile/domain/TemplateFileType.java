package ai.careerforge.profile.domain;

/** The only two formats a saved template may be — matches the extension/magic-byte check in
 *  {@code TemplateService#detectFileType}. No AI/structural analysis of either format is ever
 *  performed here; this is a plain file library, not the deleted document-service. */
public enum TemplateFileType {
    PDF,
    DOCX
}
