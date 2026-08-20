package ai.careerforge.render.template;

/** No template is registered for the requested {@link TemplateKey}, or the resource it is
 *  registered to could not be read (a missing/misconfigured classpath entry is, from a
 *  caller's point of view, indistinguishable from "not found" — both mean no usable template
 *  exists for this key right now). */
public final class TemplateNotFoundException extends TemplateLoadException {

    public TemplateNotFoundException(TemplateKey key) {
        super(key, "No built-in template registered for " + key);
    }

    public TemplateNotFoundException(TemplateKey key, String location, Throwable cause) {
        super(key, "Template registered for " + key + " could not be read from " + location);
        initCause(cause);
    }
}
