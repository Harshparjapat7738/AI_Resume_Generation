package ai.careerforge.render.html;

import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.ResumeRenderRequest;

/**
 * Converts an already-assembled, already-validated document-model request into final HTML —
 * the first stage of ADR-036's Thymeleaf → strict-XHTML → Open HTML to PDF pipeline, and
 * nothing past it: this interface returns markup, never PDF bytes, and nothing implementing it
 * persists what it returns.
 *
 * <p>Not the same abstraction as {@code DocumentRenderer} (the render-service API contract
 * step): {@code DocumentRenderer} will eventually orchestrate this stage plus strict-XHTML
 * normalisation plus the PDF conversion behind one PDF-in-PDF-out contract. This interface is
 * the narrower, internal HTML-only step that contract will call into — not itself exposed over
 * any endpoint.
 */
public interface HtmlRenderer {

    /**
     * Renders one resume to HTML.
     *
     * @throws ai.careerforge.render.template.TemplateNotFoundException       no template for
     *         {@code request.template()}
     * @throws ai.careerforge.render.template.InvalidTemplateException        the template
     *         resource itself is malformed
     * @throws ai.careerforge.render.template.UnsupportedTemplateTypeException the template
     *         resolves to a markup kind this pipeline doesn't support
     * @throws HtmlRenderException the template loaded cleanly but Thymeleaf could not process it
     *         against this request's data
     */
    String renderResume(ResumeRenderRequest request);

    /** Renders one cover letter to HTML — same failure modes as {@link #renderResume}. */
    String renderCoverLetter(CoverLetterRenderRequest request);
}
