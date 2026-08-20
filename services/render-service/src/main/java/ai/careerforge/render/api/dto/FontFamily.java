package ai.careerforge.render.api.dto;

/** The closed set of fonts render-service is allowed to embed (ADR-036's "only allowlisted
 *  fonts embedded" PDF constraint). Never a free string. */
public enum FontFamily {
    TIMES_NEW_ROMAN,
    GEORGIA,
    ARIAL,
    HELVETICA,
    CALIBRI
}
