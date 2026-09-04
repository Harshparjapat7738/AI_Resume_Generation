package ai.careerforge.render.service;

import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.RenderResponse;
import ai.careerforge.render.api.dto.ResumeRenderRequest;

/**
 * The rendering contract render-service exposes (ADR-036) — document-model-in, PDF-out, nothing
 * else. Deliberately an interface with no implementation yet: this step defines the API
 * contract only (request/response shapes, this method signature); the Thymeleaf → strict-XHTML
 * → Open HTML to PDF pipeline that fulfils it, the {@code @RestController} that routes to it,
 * and object storage are a later step's business logic, not this one's.
 *
 * <p>No AI call of any kind is ever appropriate behind this interface — an implementation never
 * holds {@code GROQ_API_KEY} and never talks to {@code ai-service}. Everything it receives is
 * already grounded and already assembled; an implementation only lays it out and verifies the
 * resulting artifact (selectable text, allowlisted embedded fonts, deterministic output, under
 * 2MB, no encryption) — it never reasons about facts.
 */
public interface DocumentRenderer {

    /** Renders one resume from an already-assembled, schema-validated document model. */
    RenderResponse renderResume(ResumeRenderRequest request);

    /** Renders one cover letter from an already-assembled, schema-validated document model. */
    RenderResponse renderCoverLetter(CoverLetterRenderRequest request);
}
