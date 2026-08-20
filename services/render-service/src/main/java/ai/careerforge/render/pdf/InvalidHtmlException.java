package ai.careerforge.render.pdf;

/** The HTML handed to {@link PdfRenderer} was rejected before conversion was even attempted —
 *  blank, or with no renderable body content. The same class of check
 *  {@code BuiltInTemplateProvider} already applies to a loaded template; this applies it again
 *  to the fully-rendered output, since a Thymeleaf processing bug could in principle still
 *  produce empty markup from a structurally valid template. */
public final class InvalidHtmlException extends PdfRenderException {

    public InvalidHtmlException(String reason) {
        super("HTML input is invalid: " + reason);
    }
}
