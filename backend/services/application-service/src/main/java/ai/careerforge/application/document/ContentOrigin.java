package ai.careerforge.application.document;

/**
 * How one {@link ContentLeaf}'s text came to exist — the platform's anti-fabrication guarantee
 * made inspectable per leaf, not just asserted in the prompt (ADR-036).
 *
 * <p>There is no third value for "invented" or "unsupported": {@code GroundingValidator}
 * (ai-service) never lets a fragment that fails grounding reach {@code application-service}'s
 * assembly step at all — a fragment dropped after both attempts is simply omitted, or replaced
 * with a {@link #VERBATIM_FROM_PROFILE} fallback built from the evidence itself. By the time a
 * leaf exists in an assembled {@link ResumeDocumentModel}/{@link CoverLetterDocumentModel}, it
 * is already one of these two, never anything else.
 */
public enum ContentOrigin {

    /** Copied unchanged from the profile evidence it cites — no model involvement, or the
     *  deterministic fallback used when the model's rewrite failed grounding twice. */
    VERBATIM_FROM_PROFILE,

    /** A grounded rewrite ai-service produced from the cited evidence — selected, ranked or
     *  rephrased, never inventing a fact the evidence doesn't already state. */
    REPHRASED_FROM_PROFILE
}
