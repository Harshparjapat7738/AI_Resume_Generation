package ai.careerforge.auth.domain;

/**
 * docs/DATABASE.md &sect;3 {@code security_events.type}. The full documented set is kept
 * here even though only a subset has a writer today (see {@code GoogleOAuthService}) — the
 * enum is the stable contract; wiring each event type into its triggering code path is
 * incremental and additive, never a breaking change to this list.
 */
public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    PASSWORD_CHANGED,
    REFRESH_REUSE,
    ACCOUNT_LOCKED,
    OAUTH_LINKED,
    EXPORT_REQUESTED,
    DELETION_REQUESTED
}
