package ai.careerforge.render.pdf;

/**
 * A {@link PdfRenderer} could not produce a {@link RenderedPdf}. Sealed to exactly the two
 * failure modes a caller needs to tell apart: the HTML input itself was unusable before
 * conversion was even attempted, or the conversion machinery failed on otherwise-acceptable
 * input. Mirrors {@code ai.careerforge.render.template.TemplateLoadException}'s shape.
 */
public sealed abstract class PdfRenderException extends RuntimeException
        permits InvalidHtmlException, PdfConversionException {

    protected PdfRenderException(String message) {
        super(message);
    }

    protected PdfRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
