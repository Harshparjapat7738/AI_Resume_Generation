package ai.careerforge.render.pdf;

/**
 * The final stage of ADR-036's Thymeleaf → strict-XHTML → Open HTML to PDF pipeline: converts
 * already-final HTML (this service's own {@code HtmlRenderer} output, or any other
 * well-formed-enough HTML) into PDF bytes. Deliberately opaque to where the HTML came from —
 * this interface never touches a {@code TemplateProvider}, a document-model request, or
 * anything upstream of the markup itself.
 *
 * <p>Nothing implementing this holds an AI credential, persists what it returns, or is exposed
 * over any endpoint yet — it is an internal abstraction a future {@code DocumentRenderer}
 * implementation will call into, one stage among several.
 */
public interface PdfRenderer {

    /**
     * Converts HTML to a PDF.
     *
     * @param html    already-final markup; not modified except for the {@code @page} rule this
     *                implementation injects from {@code options}
     * @param options page size, margins, fonts to embed, base URI for relative resources
     * @return the PDF bytes and page count
     * @throws InvalidHtmlException    {@code html} is blank or has no renderable body content
     * @throws PdfConversionException  the conversion itself failed on otherwise-acceptable input
     */
    RenderedPdf render(String html, PdfRenderOptions options);
}
