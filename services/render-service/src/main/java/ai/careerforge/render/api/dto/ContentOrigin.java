package ai.careerforge.render.api.dto;

/**
 * How one {@link ContentLeaf}'s text came to exist. Render-service's own copy of the concept
 * application-service's {@code ResumeDocumentModel}/{@code CoverLetterDocumentModel} already
 * carries (ADR-036) — there is no shared DTO module (ADR-006), so this contract is defined
 * independently here, not imported.
 *
 * <p>Presentational only from render-service's point of view: origin never changes how a leaf
 * is laid out, only that render-service — like every other consumer — never has to guess how a
 * piece of text came to exist.
 */
public enum ContentOrigin {
    VERBATIM_FROM_PROFILE,
    REPHRASED_FROM_PROFILE
}
