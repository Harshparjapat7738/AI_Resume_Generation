package ai.careerforge.render.template;

import ai.careerforge.render.api.dto.RenderTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The first {@link TemplateProvider}: application-provided, built-in HTML templates bundled on
 * render-service's own classpath — never a user upload. That remains "My Templates" (ADR-034),
 * a profile-service feature entirely; render-service never reads that bucket, and this class
 * never reads anything outside {@code classpath:templates/}.
 *
 * <p>All filesystem/classpath access is private to this class — {@link ResourceLoader} and
 * every {@link Resource}/{@link InputStream} it touches stay inside {@link #load}'s call graph
 * and never escape as a return value; callers only ever see a {@link LoadedTemplate} or a
 * {@link TemplateLoadException}.
 *
 * <p>Not a reuse of {@code document-service}: that service resolved custom, user-uploaded
 * templates through PDF structural analysis and object storage. This provider resolves a
 * closed, built-in set of HTML resources shipped with render-service itself — a different
 * mechanism for a different, narrower purpose (ADR-036).
 */
@Component
public class BuiltInTemplateProvider implements TemplateProvider {

    private final ResourceLoader resourceLoader;
    private final Map<TemplateKey, String> registry;

    /** Production wiring: Spring's {@link ResourceLoader}, the real built-in registry.
     *  {@code @Autowired} is required here, not decorative: with two constructors declared and
     *  neither the sole one, Spring cannot infer which to use for DI and falls back to a
     *  no-arg default constructor that does not exist, failing bean creation at startup — this
     *  class was never actually booted as a running Spring bean before (only unit-tested via
     *  the package-private constructor below, bypassing DI entirely) until this was caught by
     *  render-service's first real end-to-end startup. */
    @Autowired
    public BuiltInTemplateProvider(ResourceLoader resourceLoader) {
        this(resourceLoader, defaultRegistry());
    }

    /** Package-private: lets tests exercise a deliberately broken registry entry (missing,
     *  wrong extension, blank content) without reflection or a second production code path. */
    BuiltInTemplateProvider(ResourceLoader resourceLoader, Map<TemplateKey, String> registry) {
        this.resourceLoader = resourceLoader;
        this.registry = Map.copyOf(registry);
    }

    private static Map<TemplateKey, String> defaultRegistry() {
        return Map.of(
                new TemplateKey(RenderTemplate.STANDARD, DocumentKind.RESUME),
                "classpath:templates/resume/standard.html",
                new TemplateKey(RenderTemplate.STANDARD, DocumentKind.COVER_LETTER),
                "classpath:templates/cover-letter/standard.html");
    }

    @Override
    public LoadedTemplate load(TemplateKey key) {
        String location = registry.get(key);
        if (location == null) {
            throw new TemplateNotFoundException(key);
        }

        TemplateType type = inferType(key, location);
        String content = readContent(key, location);
        validateWellFormed(key, content);

        return new LoadedTemplate(key, type, content);
    }

    private TemplateType inferType(TemplateKey key, String location) {
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return TemplateType.HTML_THYMELEAF;
        }
        String extension = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : lower;
        throw new UnsupportedTemplateTypeException(key, extension);
    }

    private String readContent(TemplateKey key, String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new TemplateNotFoundException(key, location, ex);
        }
    }

    /** A deliberately shallow check — this step loads templates, it does not render them.
     *  Content must be non-blank and parse to a document whose body has something in it;
     *  anything deeper (Thymeleaf expression validity, the document-model's own fields) is a
     *  later, rendering-stage concern, not this one. */
    private void validateWellFormed(TemplateKey key, String content) {
        if (content.isBlank()) {
            throw new InvalidTemplateException(key, "template content is blank");
        }
        Document parsed = Jsoup.parse(content);
        if (parsed.body() == null || parsed.body().childNodeSize() == 0) {
            throw new InvalidTemplateException(key, "template has no renderable body content");
        }
    }
}
