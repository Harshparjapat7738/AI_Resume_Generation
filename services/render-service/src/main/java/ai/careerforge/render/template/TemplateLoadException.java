package ai.careerforge.render.template;

/**
 * A {@link TemplateProvider} could not produce a {@link LoadedTemplate}. Sealed to exactly the
 * three failure modes a caller needs to tell apart: the identifier doesn't map to anything, the
 * markup it maps to is broken, or that markup isn't a supported type. A future controller layer
 * can {@code switch} over these exhaustively rather than pattern-matching on message text.
 */
public sealed abstract class TemplateLoadException extends RuntimeException
        permits TemplateNotFoundException, InvalidTemplateException, UnsupportedTemplateTypeException {

    private final TemplateKey key;

    protected TemplateLoadException(TemplateKey key, String message) {
        super(message);
        this.key = key;
    }

    public TemplateKey key() {
        return key;
    }
}
