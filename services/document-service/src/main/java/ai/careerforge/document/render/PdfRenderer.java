package ai.careerforge.document.render;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.template.ResumeTemplate;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Resume structured data → template → PDF, matching the pipeline documented in
 * docs/ARCHITECTURE_DECISIONS.md (IMPLEMENTATION_PLAN.md &sect; document-service):
 * Thymeleaf renders the chosen template into HTML, jsoup parses and normalises it into a
 * well-formed DOM (Thymeleaf's own HTML5 output isn't guaranteed to be strict XHTML, which
 * openhtmltopdf's XML parser requires), and openhtmltopdf-pdfbox paints that DOM to PDF.
 */
@Component
public class PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

    /** Bumped whenever the rendering pipeline itself changes in a way that could change
     *  output for a given template+model (not when a template's own content changes —
     *  that's tracked by {@link ResumeTemplate#version()}). Recorded on every artifact. */
    public static final String ENGINE_VERSION = "openhtmltopdf-1.1.22+thymeleaf-1";

    private final SpringTemplateEngine templateEngine;

    public PdfRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedPdf render(ResumeTemplate template, ResumeRenderModel model) {
        String html = renderHtml(template, model);
        byte[] pdfBytes = htmlToPdf(html);
        int pageCount = countPages(pdfBytes);
        return new RenderedPdf(pdfBytes, pageCount);
    }

    private String renderHtml(ResumeTemplate template, ResumeRenderModel model) {
        Context context = new Context();
        context.setVariable("resume", model);
        return templateEngine.process(template.thymeleafTemplateName(), context);
    }

    private byte[] htmlToPdf(String html) {
        // jsoup's parser tolerates real-world HTML (unclosed tags, unquoted attributes,
        // whatever Thymeleaf's HTML5 output mode produces) and builds a well-formed tree in
        // memory; fromJsoup walks that tree directly into a W3C DOM, which is what
        // openhtmltopdf's renderer consumes. No text round-trip, so no XHTML string ever
        // has to be handwritten or validated.
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(html);
        org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDoc, "");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException ex) {
            log.warn("PDF rendering failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }
    }

    private int countPages(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (IOException ex) {
            log.warn("Could not count PDF pages: {}", ex.getMessage());
            return 1;
        }
    }

    public record RenderedPdf(byte[] bytes, int pageCount) {
    }
}
