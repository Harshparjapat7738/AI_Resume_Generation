package ai.careerforge.application.domain;

/**
 * What an {@link Application} was asked to produce for one job. See
 * ARCHITECTURE_DECISIONS.md ADR-017.
 *
 * <p>Every value is a valid, storable choice today — an {@code Application} can be created
 * with any of them. Only {@link #RESUME_ONLY} can actually reach
 * {@link ApplicationStatus#COMPLETED} right now: resume-service is the only generation
 * pipeline that exists. The other three are real domain values with no generation behind
 * them yet, not placeholders — adding cover-letter/email generation later means teaching
 * {@code ApplicationService} to satisfy them, not widening this enum.
 */
public enum GenerationType {
    RESUME_ONLY,
    COVER_LETTER_ONLY,
    EMAIL_ONLY,
    ALL
}
