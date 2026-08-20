package ai.careerforge.render.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.render.api.dto.ContentLeaf;
import ai.careerforge.render.api.dto.ContentOrigin;
import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.DocumentHeader;
import ai.careerforge.render.api.dto.FontFamily;
import ai.careerforge.render.api.dto.OutputFormat;
import ai.careerforge.render.api.dto.PageSize;
import ai.careerforge.render.api.dto.RenderHints;
import ai.careerforge.render.api.dto.RenderResponse;
import ai.careerforge.render.api.dto.RenderStatus;
import ai.careerforge.render.api.dto.RenderTemplate;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.api.dto.ResumeSection;
import ai.careerforge.render.api.dto.SectionEntry;
import ai.careerforge.render.api.dto.SectionHeading;
import ai.careerforge.render.config.ThymeleafConfig;
import ai.careerforge.render.html.HtmlRenderException;
import ai.careerforge.render.html.HtmlRenderer;
import ai.careerforge.render.html.ThymeleafHtmlRenderer;
import ai.careerforge.render.pdf.InvalidHtmlException;
import ai.careerforge.render.pdf.OpenHtmlToPdfRenderer;
import ai.careerforge.render.pdf.PdfConversionException;
import ai.careerforge.render.pdf.PdfRenderOptions;
import ai.careerforge.render.pdf.PdfRenderer;
import ai.careerforge.render.pdf.RenderedPdf;
import ai.careerforge.render.template.BuiltInTemplateProvider;
import ai.careerforge.render.template.InvalidTemplateException;
import ai.careerforge.render.template.TemplateKey;
import ai.careerforge.render.template.TemplateNotFoundException;
import ai.careerforge.render.template.UnsupportedTemplateTypeException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Integration coverage for the complete pipeline
 * (request → validation → template provider → HTML renderer → PDF renderer → response, ADR-036)
 * that {@link DocumentRenderService} orchestrates.
 *
 * <p>{@link CompletePipeline} wires the real, non-mocked collaborators built in earlier steps —
 * {@link BuiltInTemplateProvider}, {@link ThymeleafHtmlRenderer}, {@link OpenHtmlToPdfRenderer}
 * — and exercises the whole thing end to end, proving the layers actually compose, not just that
 * each compiles against the others' interfaces. {@link FailureMapping} substitutes small test
 * doubles for each renderer stage, since none of today's real inputs can actually fail
 * (the one {@link RenderTemplate} value is always registered) — this is deliberately the
 * narrower, unit-level half of this class, isolating {@link DocumentRenderService}'s own error
 * mapping from whether the real pipeline happens to be able to fail today.
 */
class DocumentRenderServiceTest {

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                "San Francisco, CA", List.of());
    }

    private static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    private static ResumeRenderRequest resumeRequest() {
        SectionEntry entry = new SectionEntry("EXP-001", "Senior Backend Engineer", "Acme Corp",
                "Remote", "2022-01", "Present",
                List.of(new ContentLeaf("Built and shipped a payments service.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE)));
        return new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(),
                new ContentLeaf("Backend engineer with 6 years of experience.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE),
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());
    }

    private static CoverLetterRenderRequest coverLetterRequest() {
        return new CoverLetterRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(),
                "Senior Backend Engineer", "Acme Corp", "Dear Hiring Manager,",
                List.of(new ContentLeaf("I'm writing to apply for the role.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE)),
                "Sincerely,", "Priya Sharma", renderHints());
    }

    @Nested
    @DisplayName("complete pipeline, real collaborators")
    class CompletePipeline {

        private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        private final HtmlRenderer htmlRenderer = new ThymeleafHtmlRenderer(
                new BuiltInTemplateProvider(resourceLoader), new ThymeleafConfig().renderTemplateEngine());
        private final PdfRenderer pdfRenderer = new OpenHtmlToPdfRenderer(resourceLoader);
        private final DocumentRenderService service = new DocumentRenderService(htmlRenderer, pdfRenderer);

        @Test
        @DisplayName("a resume request produces a real, page-counted PDF")
        void resumeRequestProducesRealPdf() {
            RenderResponse response = service.renderResume(resumeRequest());

            assertThat(response.status()).isEqualTo(RenderStatus.SUCCEEDED);
            assertThat(response.errors()).isEmpty();
            assertThat(response.document()).isNotNull();
            assertThat(response.document().documentId()).isNotBlank();
            assertThat(response.document().format()).isEqualTo(OutputFormat.PDF);
            assertThat(response.document().pageCount()).isGreaterThanOrEqualTo(1);
            assertThat(response.document().sizeBytes()).isEqualTo(response.pdfBytes().length);
            assertThat(new String(response.pdfBytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }

        @Test
        @DisplayName("a cover-letter request produces a real, page-counted PDF")
        void coverLetterRequestProducesRealPdf() {
            RenderResponse response = service.renderCoverLetter(coverLetterRequest());

            assertThat(response.status()).isEqualTo(RenderStatus.SUCCEEDED);
            assertThat(response.document().pageCount()).isGreaterThanOrEqualTo(1);
            assertThat(new String(response.pdfBytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }

        @Test
        @DisplayName("two renders of the same request produce the same page count and a non-empty PDF each time")
        void renderingIsRepeatable() {
            RenderResponse first = service.renderResume(resumeRequest());
            RenderResponse second = service.renderResume(resumeRequest());

            assertThat(first.status()).isEqualTo(RenderStatus.SUCCEEDED);
            assertThat(second.status()).isEqualTo(RenderStatus.SUCCEEDED);
            assertThat(second.document().pageCount()).isEqualTo(first.document().pageCount());
        }
    }

    @Nested
    @DisplayName("failure mapping, test-double collaborators")
    class FailureMapping {

        @Test
        @DisplayName("TemplateNotFoundException maps to a FAILED response with TEMPLATE_NOT_FOUND")
        void templateNotFoundMapsToFailedResponse() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, ai.careerforge.render.template.DocumentKind.RESUME);
            DocumentRenderService service = serviceThrowingFromHtml(new TemplateNotFoundException(key));

            RenderResponse response = service.renderResume(resumeRequest());

            assertFailedWithCode(response, "TEMPLATE_NOT_FOUND");
        }

        @Test
        @DisplayName("InvalidTemplateException maps to a FAILED response with INVALID_TEMPLATE")
        void invalidTemplateMapsToFailedResponse() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, ai.careerforge.render.template.DocumentKind.RESUME);
            DocumentRenderService service = serviceThrowingFromHtml(new InvalidTemplateException(key, "blank"));

            RenderResponse response = service.renderResume(resumeRequest());

            assertFailedWithCode(response, "INVALID_TEMPLATE");
        }

        @Test
        @DisplayName("UnsupportedTemplateTypeException maps to a FAILED response with UNSUPPORTED_TEMPLATE_TYPE")
        void unsupportedTemplateTypeMapsToFailedResponse() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, ai.careerforge.render.template.DocumentKind.RESUME);
            DocumentRenderService service = serviceThrowingFromHtml(new UnsupportedTemplateTypeException(key, "txt"));

            RenderResponse response = service.renderResume(resumeRequest());

            assertFailedWithCode(response, "UNSUPPORTED_TEMPLATE_TYPE");
        }

        @Test
        @DisplayName("HtmlRenderException maps to a FAILED response with HTML_RENDER_FAILED")
        void htmlRenderExceptionMapsToFailedResponse() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, ai.careerforge.render.template.DocumentKind.RESUME);
            DocumentRenderService service = serviceThrowingFromHtml(
                    new HtmlRenderException(key, new RuntimeException("bad expression")));

            RenderResponse response = service.renderCoverLetter(coverLetterRequest());

            assertFailedWithCode(response, "HTML_RENDER_FAILED");
        }

        @Test
        @DisplayName("InvalidHtmlException maps to a FAILED response with INVALID_HTML")
        void invalidHtmlMapsToFailedResponse() {
            DocumentRenderService service = serviceThrowingFromPdf(new InvalidHtmlException("blank"));

            RenderResponse response = service.renderResume(resumeRequest());

            assertFailedWithCode(response, "INVALID_HTML");
        }

        @Test
        @DisplayName("PdfConversionException maps to a FAILED response with PDF_CONVERSION_FAILED")
        void pdfConversionFailureMapsToFailedResponse() {
            DocumentRenderService service = serviceThrowingFromPdf(new PdfConversionException(new RuntimeException("boom")));

            RenderResponse response = service.renderResume(resumeRequest());

            assertFailedWithCode(response, "PDF_CONVERSION_FAILED");
        }

        @Test
        @DisplayName("a successful HTML+PDF pipeline (fake collaborators) still assembles a valid SUCCEEDED response")
        void successfulFakePipelineAssemblesValidResponse() {
            HtmlRenderer html = new HtmlRenderer() {
                @Override
                public String renderResume(ResumeRenderRequest request) {
                    return "<html><body><p>fake</p></body></html>";
                }

                @Override
                public String renderCoverLetter(CoverLetterRenderRequest request) {
                    throw new UnsupportedOperationException();
                }
            };
            byte[] fakePdf = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);
            PdfRenderer pdf = (h, options) -> new RenderedPdf(fakePdf, 3);
            DocumentRenderService service = new DocumentRenderService(html, pdf);

            RenderResponse response = service.renderResume(resumeRequest());

            assertThat(response.status()).isEqualTo(RenderStatus.SUCCEEDED);
            assertThat(response.document().pageCount()).isEqualTo(3);
            assertThat(response.pdfBytes()).isEqualTo(fakePdf);
        }

        private DocumentRenderService serviceThrowingFromHtml(RuntimeException toThrow) {
            HtmlRenderer html = new HtmlRenderer() {
                @Override
                public String renderResume(ResumeRenderRequest request) {
                    throw toThrow;
                }

                @Override
                public String renderCoverLetter(CoverLetterRenderRequest request) {
                    throw toThrow;
                }
            };
            PdfRenderer pdf = (h, options) -> {
                throw new AssertionError("PDF stage should never run once HTML rendering has failed");
            };
            return new DocumentRenderService(html, pdf);
        }

        private DocumentRenderService serviceThrowingFromPdf(RuntimeException toThrow) {
            HtmlRenderer html = new HtmlRenderer() {
                @Override
                public String renderResume(ResumeRenderRequest request) {
                    return "<html><body><p>fake</p></body></html>";
                }

                @Override
                public String renderCoverLetter(CoverLetterRenderRequest request) {
                    return "<html><body><p>fake</p></body></html>";
                }
            };
            PdfRenderer pdf = (h, options) -> {
                throw toThrow;
            };
            return new DocumentRenderService(html, pdf);
        }

        private void assertFailedWithCode(RenderResponse response, String expectedCode) {
            assertThat(response.status()).isEqualTo(RenderStatus.FAILED);
            assertThat(response.document()).isNull();
            assertThat(response.pdfBytes()).isNull();
            assertThat(response.errors()).extracting(ai.careerforge.render.api.dto.RenderError::code)
                    .containsExactly(expectedCode);
        }
    }
}
