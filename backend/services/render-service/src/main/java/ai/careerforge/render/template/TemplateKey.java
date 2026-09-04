package ai.careerforge.render.template;

import ai.careerforge.render.api.dto.RenderTemplate;
import java.util.Objects;

/**
 * The lookup key {@link TemplateProvider} resolves: which built-in layout, for which document
 * shape. Both parts are required — {@code RenderTemplate.STANDARD} alone is ambiguous between
 * a resume and a cover letter, which are different files with different structure.
 */
public record TemplateKey(RenderTemplate id, DocumentKind kind) {

    public TemplateKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }

    @Override
    public String toString() {
        return kind + ":" + id;
    }
}
