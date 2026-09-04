package ai.careerforge.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ai.careerforge.render.api.dto.ContentLeaf;
import ai.careerforge.render.api.dto.ContentOrigin;
import ai.careerforge.render.api.dto.DocumentHeader;
import ai.careerforge.render.api.dto.FontFamily;
import ai.careerforge.render.api.dto.OutputFormat;
import ai.careerforge.render.api.dto.PageSize;
import ai.careerforge.render.api.dto.RenderHints;
import ai.careerforge.render.api.dto.RenderTemplate;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.api.dto.ResumeSection;
import ai.careerforge.render.api.dto.SectionEntry;
import ai.careerforge.render.api.dto.SectionHeading;
import ai.careerforge.render.config.ThymeleafConfig;
import ai.careerforge.render.html.ThymeleafHtmlRenderer;
import ai.careerforge.render.template.BuiltInTemplateProvider;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.thymeleaf.TemplateEngine;

/** Covers {@link OpenHtmlToPdfRenderer}: minimal HTML, a realistic resume rendered by the
 *  actual Thymeleaf pipeline, invalid HTML, and a missing image resource. No Spring context —
 *  {@link DefaultResourceLoader} and a hand-built {@link TemplateEngine} are real, standalone
 *  implementations. */
class OpenHtmlToPdfRendererTest {

    private final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer(new DefaultResourceLoader());

    private static final String MINIMAL_HTML =
            "<html><head><title>Test</title></head><body><p>Hello, world.</p></body></html>";

    @Nested
    @DisplayName("minimal valid HTML")
    class MinimalValidHtml {

        @Test
        @DisplayName("produces real, well-formed PDF bytes")
        void producesRealPdfBytes() {
            RenderedPdf pdf = renderer.render(MINIMAL_HTML, PdfRenderOptions.defaults());

            assertThat(pdf.pageCount()).isEqualTo(1);
            assertThat(pdf.sizeBytes()).isGreaterThan(0);
            byte[] bytes = pdf.bytes();
            assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }

        @Test
        @DisplayName("A4 page size produces A4-dimensioned pages")
        void a4PageSizeProducesA4Dimensions() throws IOException {
            PdfRenderOptions options = new PdfRenderOptions(PageSize.A4, Margins.standard(), List.of(), "");

            RenderedPdf pdf = renderer.render(MINIMAL_HTML, options);

            try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
                PDRectangle box = document.getPage(0).getMediaBox();
                assertThat(box.getWidth()).isCloseTo(595.28f, within(2f));
                assertThat(box.getHeight()).isCloseTo(841.89f, within(2f));
            }
        }

        @Test
        @DisplayName("LETTER page size produces Letter-dimensioned pages, distinct from A4")
        void letterPageSizeProducesLetterDimensions() throws IOException {
            PdfRenderOptions options = new PdfRenderOptions(PageSize.LETTER, Margins.standard(), List.of(), "");

            RenderedPdf pdf = renderer.render(MINIMAL_HTML, options);

            try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
                PDRectangle box = document.getPage(0).getMediaBox();
                assertThat(box.getWidth()).isCloseTo(612f, within(2f));
                assertThat(box.getHeight()).isCloseTo(792f, within(2f));
            }
        }
    }

    @Nested
    @DisplayName("realistic resume HTML")
    class RealisticResumeHtml {

        @Test
        @DisplayName("the actual Thymeleaf-rendered resume converts to a multi-section PDF")
        void rendersActualResumeHtmlToPdf() {
            TemplateEngine templateEngine = new ThymeleafConfig().renderTemplateEngine();
            ThymeleafHtmlRenderer htmlRenderer =
                    new ThymeleafHtmlRenderer(new BuiltInTemplateProvider(new DefaultResourceLoader()), templateEngine);

            SectionEntry entry = new SectionEntry("EXP-001", "Senior Backend Engineer", "Acme Corp",
                    "Remote", "2022-01", "Present",
                    List.of(new ContentLeaf("Built and shipped a payments service handling 10,000 "
                            + "transactions per day.", List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF,
                    new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                            "San Francisco, CA", List.of()),
                    new ContentLeaf("Backend engineer with 6 years of experience.", List.of("EXP-001"),
                            ContentOrigin.REPHRASED_FROM_PROFILE),
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))),
                    new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null));

            String html = htmlRenderer.renderResume(request);
            RenderedPdf pdf = renderer.render(html, PdfRenderOptions.defaults());

            assertThat(pdf.pageCount()).isGreaterThanOrEqualTo(1);
            assertThat(pdf.sizeBytes()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("invalid HTML")
    class InvalidHtml {

        @Test
        @DisplayName("blank HTML is rejected before conversion is attempted")
        void blankHtmlIsRejected() {
            assertThatThrownBy(() -> renderer.render("   ", PdfRenderOptions.defaults()))
                    .isInstanceOf(InvalidHtmlException.class);
        }

        @Test
        @DisplayName("null HTML is rejected")
        void nullHtmlIsRejected() {
            assertThatThrownBy(() -> renderer.render(null, PdfRenderOptions.defaults()))
                    .isInstanceOf(InvalidHtmlException.class);
        }

        @Test
        @DisplayName("HTML with no renderable body content is rejected")
        void bodylessHtmlIsRejected() {
            String noBody = "<html><head><title>Empty</title></head><body></body></html>";

            assertThatThrownBy(() -> renderer.render(noBody, PdfRenderOptions.defaults()))
                    .isInstanceOf(InvalidHtmlException.class);
        }
    }

    @Nested
    @DisplayName("missing resource")
    class MissingResource {

        @Test
        @DisplayName("a missing image reference degrades gracefully — the render still succeeds")
        void missingImageDoesNotFailTheRender() {
            String htmlWithMissingImage = "<html><head><title>Test</title></head><body>"
                    + "<img src=\"does-not-exist.png\" alt=\"missing\" />"
                    + "<p>Content after the missing image.</p></body></html>";

            RenderedPdf pdf = renderer.render(htmlWithMissingImage, PdfRenderOptions.defaults());

            assertThat(pdf.pageCount()).isGreaterThanOrEqualTo(1);
            assertThat(pdf.sizeBytes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("a font resource that doesn't resolve on the classpath is skipped, not fatal")
        void missingFontResourceDoesNotFailTheRender() {
            FontResource missingFont = new FontResource("Nonexistent Family", "classpath:fonts/does-not-exist.ttf");
            PdfRenderOptions options = new PdfRenderOptions(PageSize.A4, Margins.standard(),
                    List.of(missingFont), "");

            RenderedPdf pdf = renderer.render(MINIMAL_HTML, options);

            assertThat(pdf.sizeBytes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("a font registry entry that does resolve is registered without failing the render")
        void resolvableFontResourceIsRegisteredWithoutFailure() {
            // Not a real font file — proves registration (the "resource exists" branch) doesn't
            // itself throw. Nothing in MINIMAL_HTML references this font-family, so OpenHTMLToPDF
            // never actually has to read it, matching how no built-in template references a
            // FontResource family today either (no font binaries are bundled — see FontResource).
            FontResource arbitraryExistingResource =
                    new FontResource("Unused Family", "classpath:templates/resume/standard.html");
            PdfRenderOptions options = new PdfRenderOptions(PageSize.A4, Margins.standard(),
                    List.of(arbitraryExistingResource), "");

            RenderedPdf pdf = renderer.render(MINIMAL_HTML, options);

            assertThat(pdf.sizeBytes()).isGreaterThan(0);
        }
    }
}
