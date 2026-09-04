package ai.careerforge.application.document;

/**
 * The closed set of fonts {@code render-service} is allowed to embed (ADR-036's "only
 * allowlisted fonts embedded" PDF constraint). A rendering knob, not content — deliberately an
 * enum, not a free string, so nothing upstream of rendering can request a font that was never
 * cleared for embedding.
 */
public enum FontFamily {
    TIMES_NEW_ROMAN,
    GEORGIA,
    ARIAL,
    HELVETICA,
    CALIBRI
}
