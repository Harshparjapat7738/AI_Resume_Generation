package ai.careerforge.render.template;

/**
 * The kind of markup a loaded template's content actually is. One value exists today — every
 * built-in template is Thymeleaf-flavoured HTML, per ADR-036's Thymeleaf → strict-XHTML → Open
 * HTML to PDF pipeline. Kept as an explicit, closed enum (not assumed) so a template resource
 * that doesn't resolve to a supported type is rejected by {@link TemplateProvider}, never
 * guessed at.
 */
public enum TemplateType {
    HTML_THYMELEAF
}
