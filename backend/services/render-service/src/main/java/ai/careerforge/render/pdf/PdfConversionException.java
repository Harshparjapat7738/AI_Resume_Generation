package ai.careerforge.render.pdf;

/** OpenHTMLToPDF (or the PDFBox page-count read-back after it) failed on otherwise-acceptable
 *  HTML — a CSS the renderer could not parse, a layout it could not resolve, or an I/O failure
 *  writing the output. Distinct from {@link InvalidHtmlException}: the input passed this
 *  class's own basic sanity check and only failed inside the conversion machinery itself. */
public final class PdfConversionException extends PdfRenderException {

    public PdfConversionException(Throwable cause) {
        super("PDF conversion failed: " + cause.getMessage(), cause);
    }
}
