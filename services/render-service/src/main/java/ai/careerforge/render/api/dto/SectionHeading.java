package ai.careerforge.render.api.dto;

/**
 * The closed, ATS-standard set of resume section headings render-service will fill into its
 * Thymeleaf template (ADR-036) — never an arbitrary string from upstream. Render-service's own
 * copy of application-service's enum of the same name; kept independent per ADR-006 (no shared
 * DTO module).
 */
public enum SectionHeading {
    EXPERIENCE,
    EDUCATION,
    SKILLS,
    PROJECTS,
    CERTIFICATIONS,
    ACHIEVEMENTS
}
