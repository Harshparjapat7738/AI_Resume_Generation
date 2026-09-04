package ai.careerforge.application.domain;

/**
 * The generation-lifecycle status of an {@link Application} — has this application's
 * requested content finished being produced? See ARCHITECTURE_DECISIONS.md ADR-017.
 *
 * <p>This is deliberately <strong>not</strong> the job-search tracking status
 * (applied / interviewing / offer / rejected / withdrawn) that {@code docs/DATABASE.md}'s
 * original {@code applications} sketch described — that's a later, separate concern
 * (a candidate tracking board) layered on top of a completed application, not the status of
 * generation itself. ADR-017 explains why the two are kept apart.
 */
public enum ApplicationStatus {
    /** Created; the requested generation type has not produced its artifact(s) yet. */
    DRAFT,
    /** At least one, but not all, of the requested artifacts exist. */
    PROCESSING,
    /** Every artifact the generation type requires exists. Terminal. */
    COMPLETED,
    /** Generation was attempted and failed. Not terminal — a retry moves back to PROCESSING. */
    FAILED
}
