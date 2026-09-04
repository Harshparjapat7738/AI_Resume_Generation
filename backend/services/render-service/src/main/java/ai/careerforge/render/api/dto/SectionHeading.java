package ai.careerforge.render.api.dto;

/**
 * The closed, ATS-standard set of resume section headings render-service will fill into its
 * Thymeleaf template (ADR-036) — never an arbitrary string from upstream. Render-service's own
 * copy of application-service's enum of the same name; kept independent per ADR-006 (no shared
 * DTO module).
 *
 * <p>{@link #atsLabel()} is what actually appears on the rendered page — plain, conventional
 * text, never the raw enum constant name — but it does not change the JSON wire shape: Jackson
 * still (de)serialises this enum by its constant name (e.g. {@code "EXPERIENCE"}) by default,
 * since this method carries no {@code @JsonValue}.
 */
public enum SectionHeading {
    EXPERIENCE("Experience"),
    EDUCATION("Education"),
    SKILLS("Skills"),
    PROJECTS("Projects"),
    CERTIFICATIONS("Certifications"),
    ACHIEVEMENTS("Achievements");

    private final String atsLabel;

    SectionHeading(String atsLabel) {
        this.atsLabel = atsLabel;
    }

    /** The literal heading text rendered on the document — plain and conventional on purpose. */
    public String atsLabel() {
        return atsLabel;
    }
}
