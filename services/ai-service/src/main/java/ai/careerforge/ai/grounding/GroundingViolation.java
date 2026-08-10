package ai.careerforge.ai.grounding;

/**
 * One reason a generated statement cannot be trusted.
 *
 * @param rule       which check failed
 * @param location   where in the output, e.g. {@code summary} or {@code EXP-004.bullet[1]}
 * @param detail     what was found — safe to log, contains the offending token only
 * @param evidenceId the cited evidence, when the failure relates to one
 */
public record GroundingViolation(Rule rule, String location, String detail, String evidenceId) {

    public enum Rule {
        /** Cited an evidence id that does not exist in the inventory. */
        UNKNOWN_EVIDENCE_ID,
        /** Made a factual claim with no supporting evidence cited at all. */
        MISSING_EVIDENCE_ID,
        /** Stated a number that appears nowhere in the cited evidence. */
        INVENTED_METRIC,
        /** Named a technology, employer or other proper noun absent from the profile. */
        UNSUPPORTED_ENTITY,
        /** Stated a date or year absent from the cited evidence. */
        UNSUPPORTED_DATE,
        /** Emitted contact details — these come from the profile, never from the model. */
        UNSUPPORTED_CONTACT,
        /** Emitted a URL. Generated content never contains links. */
        EXTERNAL_URL,
        /** Emitted zero-width or bidirectional control characters. */
        HIDDEN_CHARACTERS
    }
}
