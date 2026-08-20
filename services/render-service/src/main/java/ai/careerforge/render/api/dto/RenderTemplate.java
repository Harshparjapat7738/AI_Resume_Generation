package ai.careerforge.render.api.dto;

/**
 * The built-in Thymeleaf layout to fill (ADR-036) — a closed, allowlisted set, never a custom
 * upload. That remains "My Templates" (ADR-034), a separate feature profile-service owns;
 * render-service never reads a user-uploaded file as a rendering input.
 *
 * <p>One value exists today. The field is still explicit, not assumed, so adding a second
 * built-in layout later is a new enum constant, not a contract change.
 */
public enum RenderTemplate {
    STANDARD
}
