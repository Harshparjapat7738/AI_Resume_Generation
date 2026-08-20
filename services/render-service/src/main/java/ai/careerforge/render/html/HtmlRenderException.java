package ai.careerforge.render.html;

import ai.careerforge.render.template.TemplateKey;

/** The Thymeleaf engine could not process an otherwise-loaded template — malformed Thymeleaf
 *  syntax, or an expression that failed against the supplied data. Distinct from
 *  {@code TemplateLoadException}: the template was found and structurally well-formed HTML
 *  ({@code TemplateProvider} already checked that); this is a processing-time failure instead. */
public class HtmlRenderException extends RuntimeException {

    private final TemplateKey key;

    public HtmlRenderException(TemplateKey key, Throwable cause) {
        super("Failed to render HTML for template " + key + ": " + cause.getMessage(), cause);
        this.key = key;
    }

    public TemplateKey key() {
        return key;
    }
}
