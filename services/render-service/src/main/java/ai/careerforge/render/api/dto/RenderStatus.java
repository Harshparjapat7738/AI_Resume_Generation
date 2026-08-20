package ai.careerforge.render.api.dto;

/** The outcome of one render attempt — a fact, never a quality score (ADR-036: no hiring-outcome
 *  or ATS score anywhere in this pipeline). */
public enum RenderStatus {
    /** The document was rendered and every hard constraint (selectable text, allowlisted
     *  embedded fonts, deterministic output, size under 2MB, no encryption) passed. */
    SUCCEEDED,

    /** Rendering did not produce a usable artifact — see {@link RenderResponse#errors()}. */
    FAILED
}
