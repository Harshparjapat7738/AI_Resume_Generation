package ai.careerforge.render.template;

/**
 * A template successfully loaded and passed its basic integrity check — ready for a future
 * Thymeleaf engine to process. {@code content} is fully materialised text; nothing downstream
 * of {@link TemplateProvider} ever touches a file handle, an {@code InputStream}, or a
 * classpath/filesystem path directly — that access is entirely behind the provider.
 *
 * <p>No rendering happens here. This record carries markup, not a PDF, and never will —
 * turning it into a document is a later step's job.
 *
 * @param key     which template this is
 * @param type    the markup kind {@code content} actually is
 * @param content the raw template text
 */
public record LoadedTemplate(TemplateKey key, TemplateType type, String content) {

    public LoadedTemplate {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (content == null || content.isBlank()) {
            // A provider should have thrown InvalidTemplateException before reaching here —
            // this is the last-resort invariant, not the primary check.
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
