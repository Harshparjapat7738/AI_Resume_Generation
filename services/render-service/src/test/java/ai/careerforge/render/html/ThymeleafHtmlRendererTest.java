package ai.careerforge.render.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.render.api.dto.ContentLeaf;
import ai.careerforge.render.api.dto.ContentOrigin;
import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.DocumentHeader;
import ai.careerforge.render.api.dto.FontFamily;
import ai.careerforge.render.api.dto.HeaderLink;
import ai.careerforge.render.api.dto.OutputFormat;
import ai.careerforge.render.api.dto.PageSize;
import ai.careerforge.render.api.dto.RenderHints;
import ai.careerforge.render.api.dto.RenderTemplate;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.api.dto.ResumeSection;
import ai.careerforge.render.api.dto.SectionEntry;
import ai.careerforge.render.api.dto.SectionHeading;
import ai.careerforge.render.config.ThymeleafConfig;
import ai.careerforge.render.template.BuiltInTemplateProvider;
import ai.careerforge.render.template.DocumentKind;
import ai.careerforge.render.template.LoadedTemplate;
import ai.careerforge.render.template.TemplateKey;
import ai.careerforge.render.template.TemplateNotFoundException;
import ai.careerforge.render.template.TemplateProvider;
import ai.careerforge.render.template.TemplateType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.thymeleaf.TemplateEngine;

/**
 * Covers {@link ThymeleafHtmlRenderer} against the real, shipped built-in templates —
 * {@link DefaultResourceLoader} and a hand-built {@link TemplateEngine} (see
 * {@link ThymeleafConfig}) need no Spring context, keeping these true unit tests.
 */
class ThymeleafHtmlRendererTest {

    private final TemplateEngine templateEngine = new ThymeleafConfig().renderTemplateEngine();
    private final TemplateProvider templateProvider =
            new BuiltInTemplateProvider(new DefaultResourceLoader());
    private final ThymeleafHtmlRenderer renderer = new ThymeleafHtmlRenderer(templateProvider, templateEngine);

    private static DocumentHeader fullHeader() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                "San Francisco, CA", List.of(new HeaderLink("LinkedIn", "https://linkedin.com/in/priyasharma")));
    }

    private static DocumentHeader minimalHeader() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", null, null, List.of());
    }

    private static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    @Nested
    @DisplayName("valid template, fully populated data")
    class ValidTemplatePopulatedData {

        @Test
        @DisplayName("renders a resume with header, summary, sections, entries and bullets")
        void rendersFullResume() {
            SectionEntry entry = new SectionEntry("EXP-001", "Senior Backend Engineer", "Acme Corp",
                    "Remote", "2022-01", "Present",
                    List.of(new ContentLeaf("Built and shipped a payments service.", List.of("EXP-001"),
                            ContentOrigin.REPHRASED_FROM_PROFILE)));
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, fullHeader(),
                    new ContentLeaf("Backend engineer with 6 years of experience.", List.of("EXP-001"),
                            ContentOrigin.REPHRASED_FROM_PROFILE),
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());

            String html = renderer.renderResume(request);

            assertThat(html).contains("Priya Sharma")
                    .contains("priya.sharma@example.com")
                    .contains("+1 415 555 0100")
                    .contains("San Francisco, CA")
                    .contains("LinkedIn")
                    .contains("Backend engineer with 6 years of experience.")
                    .contains("Experience") // SectionHeading.atsLabel(), not the raw "EXPERIENCE" constant
                    .contains("Senior Backend Engineer")
                    .contains("Acme Corp")
                    .contains("Built and shipped a payments service.")
                    .contains("2022-01")
                    .contains("Present");
        }

        @Test
        @DisplayName("renders a cover letter with target role/company, paragraphs and signature")
        void rendersFullCoverLetter() {
            CoverLetterRenderRequest request = new CoverLetterRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, fullHeader(), "Senior Backend Engineer", "Acme Corp",
                    "Dear Hiring Manager,",
                    List.of(new ContentLeaf("I'm writing to apply for the role.", List.of("EXP-001"),
                            ContentOrigin.REPHRASED_FROM_PROFILE)),
                    "Sincerely,", "Priya Sharma", renderHints());

            String html = renderer.renderCoverLetter(request);

            assertThat(html).contains("Priya Sharma")
                    .contains("Senior Backend Engineer")
                    .contains("Acme Corp")
                    .contains("Dear Hiring Manager,")
                    // Thymeleaf escapes the apostrophe (th:text, never th:utext) — asserting the
                    // escaped form doubles as an escaping check, not just a content check.
                    .contains("I&#39;m writing to apply for the role.")
                    .contains("Sincerely,");
        }
    }

    @Nested
    @DisplayName("missing optional data")
    class MissingOptionalData {

        @Test
        @DisplayName("a resume with no summary, no phone/location/links, and no bullets still renders")
        void rendersResumeWithoutOptionalFields() {
            SectionEntry entry = new SectionEntry("SKILL-001", "Go", null, null, null, null, List.of());
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null,
                    List.of(new ResumeSection(SectionHeading.SKILLS, List.of(entry))), renderHints());

            String html = renderer.renderResume(request);

            assertThat(html).contains("Priya Sharma").contains("Go").contains("Skills");
            // No summary section content, no phone middot, no links list item.
            assertThat(html).doesNotContain("class=\"summary\"><p").doesNotContain("LinkedIn");
        }

        @Test
        @DisplayName("an entry with no endDate falls back to 'Present'")
        void ongoingEntryFallsBackToPresent() {
            SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", null,
                    "2022-01", null, List.of());
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());

            String html = renderer.renderResume(request);

            assertThat(html).contains("2022-01").contains("Present");
        }

        @Test
        @DisplayName("a cover letter with no targetRole/targetCompany still renders")
        void rendersCoverLetterWithoutTargetContext() {
            CoverLetterRenderRequest request = new CoverLetterRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null, null, "Dear Hiring Manager,",
                    List.of(new ContentLeaf("I'm writing to apply.", List.of("EXP-001"),
                            ContentOrigin.REPHRASED_FROM_PROFILE)),
                    "Sincerely,", "Priya Sharma", renderHints());

            String html = renderer.renderCoverLetter(request);

            assertThat(html).contains("Dear Hiring Manager,").contains("I&#39;m writing to apply.");
            assertThat(html).doesNotContain("Re:");
        }
    }

    @Nested
    @DisplayName("escaping hostile content")
    class Escaping {

        @Test
        @DisplayName("HTML/script-looking bullet text is emitted escaped, never as live markup")
        void hostileContentIsEscapedNotInterpreted() {
            SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", null,
                    "2022-01", "Present", List.of(
                            new ContentLeaf("Patched a job that mishandled <script>alert('x')</script> "
                                    + "input & introduced O'Brien's new rules.",
                                    List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());

            String html = renderer.renderResume(request);

            assertThat(html).doesNotContain("<script>alert('x')</script>");
            assertThat(html).contains("&lt;script&gt;").contains("&amp;");
        }
    }

    @Nested
    @DisplayName("invalid template")
    class InvalidTemplate {

        @Test
        @DisplayName("Thymeleaf syntax errors in an otherwise-loaded template are wrapped as HtmlRenderException")
        void malformedThymeleafSyntaxIsWrapped() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME);
            // An unterminated expression — this string is well-formed HTML (BuiltInTemplateProvider
            // would happily load it) but broken Thymeleaf syntax, so it fails at processing time.
            String brokenMarkup = "<html><body><p th:text=\"${header.fullName() + }\">x</p></body></html>";
            TemplateProvider brokenProvider = k -> new LoadedTemplate(key, TemplateType.HTML_THYMELEAF, brokenMarkup);
            ThymeleafHtmlRenderer rendererWithBrokenTemplate =
                    new ThymeleafHtmlRenderer(brokenProvider, templateEngine);
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE,
                            List.of(new SectionEntry("EXP-001", "Engineer", null, null, null, null, List.of())))),
                    renderHints());

            assertThatThrownBy(() -> rendererWithBrokenTemplate.renderResume(request))
                    .isInstanceOf(HtmlRenderException.class)
                    .extracting(ex -> ((HtmlRenderException) ex).key())
                    .isEqualTo(key);
        }

        @Test
        @DisplayName("a template-loading failure (e.g. unknown identifier) propagates untouched, not wrapped")
        void templateLoadFailurePropagatesUntouched() {
            TemplateProvider alwaysMissing = k -> {
                throw new TemplateNotFoundException(k);
            };
            ThymeleafHtmlRenderer rendererWithMissingTemplate =
                    new ThymeleafHtmlRenderer(alwaysMissing, templateEngine);
            ResumeRenderRequest request = new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD,
                    OutputFormat.PDF, minimalHeader(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE,
                            List.of(new SectionEntry("EXP-001", "Engineer", null, null, null, null, List.of())))),
                    renderHints());

            assertThatThrownBy(() -> rendererWithMissingTemplate.renderResume(request))
                    .isInstanceOf(TemplateNotFoundException.class);
        }
    }
}
