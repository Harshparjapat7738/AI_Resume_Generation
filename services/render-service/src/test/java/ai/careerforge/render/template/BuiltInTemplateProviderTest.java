package ai.careerforge.render.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.render.api.dto.RenderTemplate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

/** Covers {@link BuiltInTemplateProvider}: successful loading of both real, shipped built-in
 *  templates, and each of the three documented failure modes. {@link DefaultResourceLoader} is
 *  a real, lightweight {@link ResourceLoader} — no Spring context needed for these to be true
 *  unit tests. */
class BuiltInTemplateProviderTest {

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Nested
    @DisplayName("successful loading")
    class SuccessfulLoading {

        private final BuiltInTemplateProvider provider = new BuiltInTemplateProvider(resourceLoader);

        @Test
        @DisplayName("loads the real, shipped standard resume template")
        void loadsStandardResumeTemplate() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME);

            LoadedTemplate loaded = provider.load(key);

            assertThat(loaded.key()).isEqualTo(key);
            assertThat(loaded.type()).isEqualTo(TemplateType.HTML_THYMELEAF);
            assertThat(loaded.content()).contains("<html").contains("</html>");
        }

        @Test
        @DisplayName("loads the real, shipped standard cover-letter template")
        void loadsStandardCoverLetterTemplate() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.COVER_LETTER);

            LoadedTemplate loaded = provider.load(key);

            assertThat(loaded.key()).isEqualTo(key);
            assertThat(loaded.type()).isEqualTo(TemplateType.HTML_THYMELEAF);
            assertThat(loaded.content()).contains("<html").contains("</html>");
        }

        @Test
        @DisplayName("the resume and cover-letter templates are genuinely different files")
        void resumeAndCoverLetterTemplatesAreDistinct() {
            LoadedTemplate resume = provider.load(new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME));
            LoadedTemplate coverLetter =
                    provider.load(new TemplateKey(RenderTemplate.STANDARD, DocumentKind.COVER_LETTER));

            assertThat(resume.content()).isNotEqualTo(coverLetter.content());
        }
    }

    @Nested
    @DisplayName("template not found")
    class NotFound {

        @Test
        @DisplayName("a key with no registry entry at all is rejected")
        void unregisteredKeyIsRejected() {
            BuiltInTemplateProvider provider = new BuiltInTemplateProvider(resourceLoader, Map.of());
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME);

            assertThatThrownBy(() -> provider.load(key))
                    .isInstanceOf(TemplateNotFoundException.class)
                    .extracting(ex -> ((TemplateNotFoundException) ex).key())
                    .isEqualTo(key);
        }

        @Test
        @DisplayName("a registry entry pointing at a classpath resource that doesn't exist is rejected")
        void registeredButUnreadableResourceIsRejected() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME);
            BuiltInTemplateProvider provider = new BuiltInTemplateProvider(resourceLoader,
                    Map.of(key, "classpath:templates/does-not-exist.html"));

            assertThatThrownBy(() -> provider.load(key))
                    .isInstanceOf(TemplateNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("invalid template")
    class Invalid {

        @Test
        @DisplayName("blank template content is rejected")
        void blankContentIsRejected() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME);
            BuiltInTemplateProvider provider = new BuiltInTemplateProvider(resourceLoader,
                    Map.of(key, "classpath:templates/test/blank.html"));

            assertThatThrownBy(() -> provider.load(key))
                    .isInstanceOf(InvalidTemplateException.class)
                    .extracting(ex -> ((InvalidTemplateException) ex).reason())
                    .isEqualTo("template content is blank");
        }
    }

    @Nested
    @DisplayName("unsupported template type")
    class UnsupportedType {

        @Test
        @DisplayName("a non-HTML registry entry is rejected before any content is even read")
        void nonHtmlExtensionIsRejected() {
            TemplateKey key = new TemplateKey(RenderTemplate.STANDARD, DocumentKind.COVER_LETTER);
            BuiltInTemplateProvider provider = new BuiltInTemplateProvider(resourceLoader,
                    Map.of(key, "classpath:templates/test/not-html.txt"));

            assertThatThrownBy(() -> provider.load(key))
                    .isInstanceOf(UnsupportedTemplateTypeException.class)
                    .extracting(ex -> ((UnsupportedTemplateTypeException) ex).declaredType())
                    .isEqualTo("txt");
        }
    }
}
