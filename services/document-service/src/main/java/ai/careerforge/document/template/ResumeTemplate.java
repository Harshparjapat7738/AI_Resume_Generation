package ai.careerforge.document.template;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.util.Arrays;

/**
 * The renderable side of the CareerForge template split (ARCHITECTURE_DECISIONS.md ADR-004,
 * ADR-016): resume-service owns the selectable catalogue — display name, description,
 * preview, ATS-safe flag — that a user actually chooses from (see resume-service's
 * {@code Template}/{@code TemplateSeeder}, served at {@code GET /api/resumes/templates}).
 * This enum only maps the same three ids to the versioned HTML/CSS asset that renders them;
 * it deliberately does not re-expose display metadata resume-service already owns and
 * serves — see ADR-016's "Impact": document-service closes the rendering gap that ADR
 * identified, it doesn't duplicate the catalogue.
 *
 * <p>Ids and default must stay in lockstep with resume-service's {@code TemplateSeeder} and
 * {@code TemplateService#DEFAULT_TEMPLATE_ID}.
 */
public enum ResumeTemplate {

    CLASSIC("classic", "v1"),
    MODERN_ATS("modern-ats", "v1"),
    PROFESSIONAL("professional", "v1");

    private final String id;
    private final String version;

    ResumeTemplate(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    /** Classpath location of this template's HTML resource, resolved via Thymeleaf's
     *  {@code classpath:/templates/} prefix + {@code .html} suffix (Spring Boot default). */
    public String thymeleafTemplateName() {
        return id + "/" + version + "/resume";
    }

    /** Matches resume-service's {@code TemplateService#DEFAULT_TEMPLATE_ID} — used only when
     *  a resume version predates template selection entirely (no {@code templateId} stored
     *  at all), not the normal path, which uses whatever was selected at generation time. */
    public static final ResumeTemplate DEFAULT = CLASSIC;

    public static ResumeTemplate fromId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(t -> t.id.equalsIgnoreCase(templateId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Unknown template '" + sanitize(templateId) + "'. Choose one of: classic, modern-ats, professional."));
    }

    /** Defensive: the invalid id is echoed back in an error message, so strip anything that
     *  isn't plain alphanumerics/dash before it reaches the client-facing string. */
    private static String sanitize(String value) {
        String trimmed = value.strip();
        String cleaned = trimmed.replaceAll("[^a-zA-Z0-9-]", "");
        return cleaned.isBlank() ? "?" : cleaned.substring(0, Math.min(cleaned.length(), 40));
    }
}
