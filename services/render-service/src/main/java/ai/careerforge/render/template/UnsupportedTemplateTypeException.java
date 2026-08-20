package ai.careerforge.render.template;

/** A template resource resolved to a markup kind {@link TemplateProvider} does not know how to
 *  hand to the renderer (ADR-036: HTML/Thymeleaf only — no DOCX, no other format). Distinct
 *  from {@link InvalidTemplateException}: the content may be perfectly well-formed in its own
 *  right, just not a type this pipeline supports. */
public final class UnsupportedTemplateTypeException extends TemplateLoadException {

    private final String declaredType;

    public UnsupportedTemplateTypeException(TemplateKey key, String declaredType) {
        super(key, "Template " + key + " has unsupported type '" + declaredType + "'; only "
                + TemplateType.HTML_THYMELEAF + " is supported");
        this.declaredType = declaredType;
    }

    public String declaredType() {
        return declaredType;
    }
}
