package ai.careerforge.render.template;

/** A template resource was found and read, but its content failed the basic structural
 *  integrity check (blank, or does not parse to a document with a non-empty body) — a
 *  configuration defect in the built-in template itself, not something a caller can fix by
 *  retrying. */
public final class InvalidTemplateException extends TemplateLoadException {

    private final String reason;

    public InvalidTemplateException(TemplateKey key, String reason) {
        super(key, "Template " + key + " is invalid: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
