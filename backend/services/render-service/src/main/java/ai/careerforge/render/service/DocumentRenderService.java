package ai.careerforge.render.service;

import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.DocumentMetadata;
import ai.careerforge.render.api.dto.OutputFormat;
import ai.careerforge.render.api.dto.RenderError;
import ai.careerforge.render.api.dto.RenderHints;
import ai.careerforge.render.api.dto.RenderResponse;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.html.HtmlRenderException;
import ai.careerforge.render.html.HtmlRenderer;
import ai.careerforge.render.pdf.InvalidHtmlException;
import ai.careerforge.render.pdf.Margins;
import ai.careerforge.render.pdf.PdfConversionException;
import ai.careerforge.render.pdf.PdfRenderException;
import ai.careerforge.render.pdf.PdfRenderOptions;
import ai.careerforge.render.pdf.PdfRenderer;
import ai.careerforge.render.pdf.RenderedPdf;
import ai.careerforge.render.template.InvalidTemplateException;
import ai.careerforge.render.template.TemplateLoadException;
import ai.careerforge.render.template.TemplateNotFoundException;
import ai.careerforge.render.template.UnsupportedTemplateTypeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The orchestration layer render-service's API contract sits on top of (ADR-036): resolve
 * template + render HTML ({@link HtmlRenderer} — a {@code TemplateProvider} lookup is already
 * inside it, so this class never touches one directly), generate PDF ({@link PdfRenderer}),
 * assemble the response.
 *
 * <p>Request-shape validation happens before this class is ever called — {@code @Valid} on
 * {@code RenderController}'s {@code @RequestBody} parameters, enforced by Jakarta Bean
 * Validation through Spring MVC, rejects a malformed request with a standard {@code ApiError}
 * (platform-common's {@code GlobalExceptionHandler}) before {@code renderResume}/
 * {@code renderCoverLetter} runs at all. This class only ever sees an already-valid request.
 *
 * <p><strong>Every typed exception the pipeline can throw is caught here</strong> — the whole
 * point of this class existing, beyond wiring the two renderers together, is that a failure
 * anywhere in template resolution, HTML rendering or PDF conversion becomes a
 * {@link RenderResponse#failed(List)} with a stable {@link RenderError#code()}, never an
 * unhandled exception that would otherwise fall through to platform-common's generic 500. A
 * render that didn't work is a normal, expected outcome of this operation, not a protocol-level
 * error — the caller gets a 200 either way and reads {@code status} to know which happened.
 */
@Service
public class DocumentRenderService implements DocumentRenderer {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderService.class);

    private final HtmlRenderer htmlRenderer;
    private final PdfRenderer pdfRenderer;

    public DocumentRenderService(HtmlRenderer htmlRenderer, PdfRenderer pdfRenderer) {
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    @Override
    public RenderResponse renderResume(ResumeRenderRequest request) {
        return render(() -> htmlRenderer.renderResume(request), request.outputFormat(), request.renderHints());
    }

    @Override
    public RenderResponse renderCoverLetter(CoverLetterRenderRequest request) {
        return render(() -> htmlRenderer.renderCoverLetter(request), request.outputFormat(), request.renderHints());
    }

    private RenderResponse render(Supplier<String> resolveTemplateAndRenderHtml, OutputFormat outputFormat,
                                  RenderHints renderHints) {
        String html;
        try {
            // Resolve template + render HTML — HtmlRenderer already calls TemplateProvider
            // internally, so both pipeline stages happen in this one call.
            html = resolveTemplateAndRenderHtml.get();
        } catch (TemplateLoadException ex) {
            log.warn("Template resolution failed: {}", ex.getMessage());
            return RenderResponse.failed(List.of(new RenderError(templateErrorCode(ex), ex.getMessage())));
        } catch (HtmlRenderException ex) {
            log.warn("HTML rendering failed: {}", ex.getMessage());
            return RenderResponse.failed(List.of(new RenderError("HTML_RENDER_FAILED", ex.getMessage())));
        }

        RenderedPdf pdf;
        try {
            pdf = pdfRenderer.render(html, toPdfOptions(renderHints));
        } catch (PdfRenderException ex) {
            log.warn("PDF conversion failed: {}", ex.getMessage());
            return RenderResponse.failed(List.of(new RenderError(pdfErrorCode(ex), ex.getMessage())));
        }

        DocumentMetadata metadata = new DocumentMetadata(
                // No persistence exists yet (this workstream's steps are explicitly scoped to
                // stop short of it) — a random id stands in for a real object-store key a future
                // step would assign when the PDF is actually written to render-service's own
                // bucket. See RenderResponse's own Javadoc on why the bytes travel inline today.
                UUID.randomUUID().toString(), outputFormat, pdf.sizeBytes(), pdf.pageCount(), Instant.now());

        return RenderResponse.succeeded(metadata, pdf.bytes());
    }

    /** {@code RenderHints} already carries everything {@code PdfRenderOptions} needs today —
     *  {@code pageSize} maps straight across (the same enum, not a second vocabulary); margins
     *  default (no per-request margin field exists yet); no fonts are registered (no font
     *  binaries are bundled — see {@code FontResource}'s own Javadoc for why); no base URI (no
     *  built-in template references an external image). */
    private PdfRenderOptions toPdfOptions(RenderHints renderHints) {
        return new PdfRenderOptions(renderHints.pageSize(), Margins.standard(), List.of(), "");
    }

    private String templateErrorCode(TemplateLoadException ex) {
        return switch (ex) {
            case TemplateNotFoundException ignored -> "TEMPLATE_NOT_FOUND";
            case InvalidTemplateException ignored -> "INVALID_TEMPLATE";
            case UnsupportedTemplateTypeException ignored -> "UNSUPPORTED_TEMPLATE_TYPE";
        };
    }

    private String pdfErrorCode(PdfRenderException ex) {
        return switch (ex) {
            case InvalidHtmlException ignored -> "INVALID_HTML";
            case PdfConversionException ignored -> "PDF_CONVERSION_FAILED";
        };
    }
}
