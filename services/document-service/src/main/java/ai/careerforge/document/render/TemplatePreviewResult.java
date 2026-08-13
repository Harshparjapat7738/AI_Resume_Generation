package ai.careerforge.document.render;

import ai.careerforge.document.domain.DocumentFormat;

/**
 * The bytes of a template rendered against {@link SampleResumeRenderModel#sample()} — never
 * persisted (see {@code DocumentRenderService#renderPreview}), so unlike {@code RenderedDocument}
 * this is just a value returned straight to the controller and streamed to the browser.
 */
public record TemplatePreviewResult(byte[] bytes, DocumentFormat format) {
}
