package ai.careerforge.auth.domain;

/** Account lifecycle state. Only {@code ACTIVE} may sign in. */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED,
    PENDING_DELETION
}
