package ai.careerforge.render.pdf;

import ai.careerforge.render.api.dto.PageSize;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The dedicated PDF-conversion component (ADR-036): jsoup/{@link W3CDom} strict-XHTML
 * normalisation, then Open HTML to PDF (PDFBox). Filesystem/classpath access for font files is
 * entirely private to this class, via {@link ResourceLoader} — the same pattern
 * {@code BuiltInTemplateProvider} already uses for templates.
 *
 * <p><strong>Strict-XHTML normalisation</strong>: OpenHTMLToPDF's renderer requires well-formed
 * XML input, which Thymeleaf's own HTML output does not strictly guarantee. Rather than hand it
 * the raw HTML string, this class parses it with jsoup — the same lenient HTML5 parser browsers
 * use — and converts the resulting tree straight to a {@code org.w3c.dom.Document} via
 * {@link W3CDom}, which is always well-formed by construction. No re-serialisation to a string
 * and re-parse happens in between, so nothing is double-escaped or corrupted along the way.
 *
 * <p><strong>Page size and margins</strong> are applied as a CSS {@code @page} rule this class
 * injects into the parsed document's {@code <head>} — never baked into a Thymeleaf template, so
 * the same template can be rendered at A4 or Letter, with different margins, purely by varying
 * {@link PdfRenderOptions}.
 *
 * <p><strong>Fonts</strong>: every {@link FontResource} in {@link PdfRenderOptions#fonts()} is
 * registered with the builder if its classpath location resolves to a real file; one that
 * doesn't is skipped, not failed — the CSS {@code font-family} stack falls back past it. No font
 * embedding at all is a valid, working configuration ({@link PdfRenderOptions#fonts()} may be
 * empty): every PDF viewer already supports the PDF base-14 standard fonts OpenHTMLToPDF falls
 * back to.
 *
 * <p><strong>Images</strong>: relative {@code <img>}/resource URIs resolve against
 * {@link PdfRenderOptions#baseUri()}. A missing image is not a hard failure — OpenHTMLToPDF logs
 * a warning and lays out the page without it, verified directly in
 * {@code OpenHtmlToPdfRendererTest}.
 */
@Component
public class OpenHtmlToPdfRenderer implements PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(OpenHtmlToPdfRenderer.class);

    private final ResourceLoader resourceLoader;

    public OpenHtmlToPdfRenderer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public RenderedPdf render(String html, PdfRenderOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        Document jsoupDocument = parseAndValidate(html);
        applyPageSetup(jsoupDocument, options);
        org.w3c.dom.Document w3cDocument = new W3CDom().fromJsoup(jsoupDocument);

        byte[] pdfBytes = convert(w3cDocument, options);
        int pageCount = countPages(pdfBytes);

        return new RenderedPdf(pdfBytes, pageCount);
    }

    /** The same blank/no-body-content check {@code BuiltInTemplateProvider} applies to a
     *  loaded template, applied again here to the fully-rendered output. */
    private Document parseAndValidate(String html) {
        if (html == null || html.isBlank()) {
            throw new InvalidHtmlException("HTML content is blank");
        }
        Document document = Jsoup.parse(html);
        if (document.body() == null || document.body().childNodeSize() == 0) {
            throw new InvalidHtmlException("HTML has no renderable body content");
        }
        return document;
    }

    private void applyPageSetup(Document document, PdfRenderOptions options) {
        Margins margins = options.margins();
        String css = "@page { size: " + cssPageSize(options.pageSize()) + "; margin: "
                + margins.topPt() + "pt " + margins.rightPt() + "pt "
                + margins.bottomPt() + "pt " + margins.leftPt() + "pt; }";
        document.head().appendElement("style").text(css);
    }

    private String cssPageSize(PageSize pageSize) {
        return switch (pageSize) {
            case A4 -> "A4";
            case LETTER -> "letter";
        };
    }

    private byte[] convert(org.w3c.dom.Document w3cDocument, PdfRenderOptions options) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDocument, options.baseUri());
            for (FontResource font : options.fonts()) {
                registerFont(builder, font);
            }
            builder.toStream(output);
            builder.run();
        } catch (Exception ex) {
            throw new PdfConversionException(ex);
        }
        return output.toByteArray();
    }

    /** Never fails the render — a missing font resource is logged and simply not registered,
     *  letting the CSS font-family fallback stack (and ultimately a PDF base-14 standard font)
     *  take over, exactly like a missing {@code <img>} degrades rather than crashes. */
    private void registerFont(PdfRendererBuilder builder, FontResource font) {
        Resource resource = resourceLoader.getResource(font.classpathLocation());
        if (!resource.exists()) {
            log.warn("Font resource not found, skipping: family={} location={}",
                    font.familyName(), font.classpathLocation());
            return;
        }
        builder.useFont(() -> openStream(resource), font.familyName());
    }

    private InputStream openStream(Resource resource) {
        try {
            return resource.getInputStream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private int countPages(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new PdfConversionException(ex);
        }
    }
}
