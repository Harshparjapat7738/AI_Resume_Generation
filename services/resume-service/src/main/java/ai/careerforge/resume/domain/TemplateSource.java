package ai.careerforge.resume.domain;

/**
 * Where a template came from — the three sources described in the product goal, added
 * incrementally. {@link #BUILT_IN} and {@link #CUSTOM_UPLOAD} (DOCX and, since ADR-023, PDF)
 * both have real rows; see ARCHITECTURE_DECISIONS.md ADR-016 for why {@link #ONLINE} alone
 * remains reserved rather than built.
 */
public enum TemplateSource {
    BUILT_IN,
    CUSTOM_UPLOAD,
    ONLINE
}
