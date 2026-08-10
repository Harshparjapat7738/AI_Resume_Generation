package ai.careerforge.resume.domain;

/**
 * Which generated-document kind a template applies to. Only {@link #RESUME} has any rows or
 * any consumer today — cover letter and email generation are out of scope for the template
 * system's first phase (see ARCHITECTURE_DECISIONS.md ADR-016). The values exist now so the
 * catalogue doesn't need a breaking shape change when those features ship.
 */
public enum TemplateType {
    RESUME,
    COVER_LETTER,
    EMAIL
}
