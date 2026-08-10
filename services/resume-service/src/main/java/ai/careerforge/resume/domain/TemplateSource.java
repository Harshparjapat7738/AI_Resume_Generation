package ai.careerforge.resume.domain;

/**
 * Where a template came from — the three sources described in the product goal, added
 * incrementally. Only {@link #BUILT_IN} has any rows today; see ARCHITECTURE_DECISIONS.md
 * ADR-016 for why {@link #CUSTOM_UPLOAD} and {@link #ONLINE} are reserved rather than built.
 */
public enum TemplateSource {
    BUILT_IN,
    CUSTOM_UPLOAD,
    ONLINE
}
