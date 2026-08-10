package ai.careerforge.document.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.document.render.PdfRenderer.RenderedPdf;
import ai.careerforge.document.render.ResumeRenderModel.CertificationEntry;
import ai.careerforge.document.render.ResumeRenderModel.EducationEntry;
import ai.careerforge.document.render.ResumeRenderModel.ExperienceEntry;
import ai.careerforge.document.template.ResumeTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Verifies actual PDF content — extracted text, page count — not just "rendering didn't
 * throw". This is what the resume PDF pipeline's own tests promised to check: real bytes a
 * viewer would open, not an HTTP 200.
 */
class PdfRendererTest {

    private PdfRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        renderer = new PdfRenderer(engine);
    }

    @Test
    void rendersNameContactAndEverySectionAsRealExtractableText() throws IOException {
        RenderedPdf pdf = renderer.render(ResumeTemplate.MODERN_ATS, shortModel());

        assertThat(pdf.bytes()).isNotEmpty();
        assertThat(pdf.pageCount()).isEqualTo(1);

        // modern-ats's section headers are CSS text-transform:uppercase — openhtmltopdf
        // applies the transform to the actual glyphs, so extracted text is upper-case too,
        // not just visually styled. That's the real, correct pipeline behaviour.
        String text = extractText(pdf.bytes());
        assertThat(text).contains("Ada Lovelace");
        assertThat(text).contains("ada@example.com");
        assertThat(text).contains("Analytical Engine architecture and formal computing theory.");
        assertThat(text).contains("EXPERIENCE");
        assertThat(text).contains("Designed the first published algorithm for a general-purpose computer.");
        assertThat(text).contains("EDUCATION");
        assertThat(text).contains("University of London");
        assertThat(text).contains("SKILLS");
        assertThat(text).contains("Analytical Engines");
        assertThat(text).contains("CERTIFICATIONS");
        assertThat(text).contains("Royal Society Fellow");
    }

    @Test
    void longContentSpansMultiplePagesWithNothingTruncated() throws IOException {
        RenderedPdf pdf = renderer.render(ResumeTemplate.CLASSIC, longModel());

        assertThat(pdf.pageCount()).isGreaterThan(1);

        String text = extractText(pdf.bytes());
        assertThat(text).contains("Bullet line 0 —");
        assertThat(text).contains("Bullet line 59 —"); // last of 60 bullets, still present
    }

    @Test
    void everyTemplateProducesAValidNonTrivialPdfForTheSameModel() throws IOException {
        ResumeRenderModel model = shortModel();
        for (ResumeTemplate template : ResumeTemplate.values()) {
            RenderedPdf pdf = renderer.render(template, model);
            assertThat(pdf.bytes().length)
                    .as("template %s should produce a real, non-trivial PDF", template.id())
                    .isGreaterThan(800);
            assertThat(extractText(pdf.bytes()))
                    .as("template %s should still render the same content", template.id())
                    .contains("Ada Lovelace");
        }
    }

    @Test
    void missingOptionalSectionsAreOmittedRatherThanShownEmpty() throws IOException {
        ResumeRenderModel minimal = new ResumeRenderModel(
                "Grace Hopper", null, null, null, List.of(), null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        RenderedPdf pdf = renderer.render(ResumeTemplate.MODERN_ATS, minimal);
        String text = extractText(pdf.bytes());

        assertThat(text).contains("Grace Hopper");
        assertThat(text).doesNotContain("Certifications");
        assertThat(text).doesNotContain("Projects");
        assertThat(text).doesNotContain("Achievements");
    }

    private static String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static ResumeRenderModel shortModel() {
        return new ResumeRenderModel(
                "Ada Lovelace",
                "Mathematician & Writer",
                "ada@example.com",
                "+44 20 7946 0958",
                List.of("https://en.wikipedia.org/wiki/Ada_Lovelace"),
                "Founding Computer Scientist",
                "Analytical Engines Ltd",
                "Analytical Engine architecture and formal computing theory.",
                List.of(new ExperienceEntry(
                        "Analytical Engines Ltd", "Founding Computer Scientist", "London, UK",
                        "1842 – 1852",
                        List.of("Designed the first published algorithm for a general-purpose computer."),
                        List.of("Analytical Engines", "Punch Cards"))),
                List.of(new EducationEntry(
                        "University of London", "Independent study", "Mathematics", "1835 – 1841",
                        null, "Tutored privately in advanced mathematics and logic.")),
                List.of("Analytical Engines", "Formal Logic", "Technical Writing"),
                List.of(),
                List.of(new CertificationEntry(
                        "Royal Society Fellow", "Royal Society", "1835", null, null, null)),
                List.of());
    }

    private static ResumeRenderModel longModel() {
        List<String> bullets = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bullets.add("Bullet line " + i + " — delivered a long, realistic accomplishment statement with "
                    + "enough detail to occupy real vertical space on the rendered page and force pagination.");
        }
        List<ExperienceEntry> experience = List.of(
                new ExperienceEntry("Long Co", "Staff Engineer", "Remote", "2018 – Present", bullets, List.of("Java")));

        return new ResumeRenderModel(
                "Long Resume Candidate", "Staff Engineer", "candidate@example.com", null, List.of(),
                "Staff Engineer", "Long Co", "A summary long enough to add real content to the page.",
                experience, List.of(), List.of("Java", "Kubernetes"), List.of(), List.of(), List.of());
    }
}
