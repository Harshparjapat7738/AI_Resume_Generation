package ai.careerforge.render.html;

import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.template.DocumentKind;
import ai.careerforge.render.template.LoadedTemplate;
import ai.careerforge.render.template.TemplateKey;
import ai.careerforge.render.template.TemplateProvider;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

/**
 * The dedicated rendering component (ADR-036): fills a {@link LoadedTemplate}, obtained from
 * {@link TemplateProvider} — never a filesystem/classpath path this class touches itself —
 * with an already-grounded, already-assembled document-model request, and returns HTML.
 *
 * <p><strong>Deterministic.</strong> Given the same template content and the same request, the
 * output is byte-for-byte identical: no randomness, no current-time/locale-dependent values are
 * ever bound into the template context, and every collection this class iterates
 * ({@code sections}, {@code entries}, {@code bullets}, {@code paragraphs}, {@code links}) is
 * already an ordered {@code List} the caller controls — Thymeleaf's {@code th:each} preserves
 * that order exactly.
 *
 * <p><strong>Escaping.</strong> Every template this class feeds uses Thymeleaf's escaped output
 * attributes ({@code th:text}, never {@code th:utext}) for every candidate-supplied field — a
 * bullet containing {@code <script>} or {@code &} is emitted as inert, HTML-entity-escaped text,
 * never live markup. This class does not itself escape anything; it relies on Thymeleaf's own
 * {@code TemplateMode.HTML} escaping being applied correctly by every built-in template, which
 * {@link ai.careerforge.render.html.ThymeleafHtmlRendererTest} verifies directly against hostile
 * content.
 *
 * <p>No PDF is produced here, and nothing this class returns is persisted — this is the
 * HTML-fill stage only.
 */
@Component
public class ThymeleafHtmlRenderer implements HtmlRenderer {

    private final TemplateProvider templateProvider;
    private final TemplateEngine templateEngine;

    public ThymeleafHtmlRenderer(TemplateProvider templateProvider, TemplateEngine renderTemplateEngine) {
        this.templateProvider = templateProvider;
        this.templateEngine = renderTemplateEngine;
    }

    @Override
    public String renderResume(ResumeRenderRequest request) {
        TemplateKey key = new TemplateKey(request.template(), DocumentKind.RESUME);
        LoadedTemplate template = templateProvider.load(key);

        Context context = new Context();
        context.setVariable("header", request.header());
        context.setVariable("summary", request.summary());
        context.setVariable("sections", request.sections());

        return process(template, context);
    }

    @Override
    public String renderCoverLetter(CoverLetterRenderRequest request) {
        TemplateKey key = new TemplateKey(request.template(), DocumentKind.COVER_LETTER);
        LoadedTemplate template = templateProvider.load(key);

        Context context = new Context();
        context.setVariable("header", request.header());
        context.setVariable("targetRole", request.targetRole());
        context.setVariable("targetCompany", request.targetCompany());
        context.setVariable("salutation", request.salutation());
        context.setVariable("paragraphs", request.paragraphs());
        context.setVariable("closing", request.closing());
        context.setVariable("signatureName", request.signatureName());

        return process(template, context);
    }

    private String process(LoadedTemplate template, Context context) {
        try {
            // A StringTemplateResolver (see ThymeleafConfig) treats this string argument as the
            // template's own content, not a name to resolve — the engine never looks anywhere
            // on disk or the classpath itself; TemplateProvider already did all resource access.
            return templateEngine.process(template.content(), context);
        } catch (TemplateEngineException ex) {
            throw new HtmlRenderException(template.key(), ex);
        }
    }
}
