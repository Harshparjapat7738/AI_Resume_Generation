package ai.careerforge.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The single error envelope returned by every CareerForge service.
 *
 * <pre>
 * {
 *   "timestamp": "2026-08-09T10:15:30Z",
 *   "status": 400,
 *   "code": "JD_VALIDATION_ERROR",
 *   "message": "Unable to process the supplied job description.",
 *   "path": "/api/jd",
 *   "correlationId": "8f14e45f-...",
 *   "fieldErrors": [{"field": "url", "message": "must be a valid URL"}]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
                correlationId, List.of());
    }

    public static ApiError of(ErrorCode code, String message, String path, String correlationId,
                              List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
                correlationId, fieldErrors);
    }
}
