package ai.careerforge.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to clients.
 *
 * <p>Codes are part of the public API contract: the frontend switches on them. Add new
 * values rather than repurposing existing ones, and mirror every addition in
 * docs/API_CATALOG.md.
 */
public enum ErrorCode {

    // --- generic ---------------------------------------------------------
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request contains invalid data."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be parsed."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have access to this resource."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please retry shortly."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is unavailable."),

    // --- domain ----------------------------------------------------------
    JD_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Unable to process the supplied job description."),
    JD_URL_BLOCKED(HttpStatus.BAD_REQUEST, "The supplied URL is not permitted."),
    JD_NOT_CONFIRMED(HttpStatus.CONFLICT, "The job description must be confirmed before this action."),
    FILE_REJECTED(HttpStatus.BAD_REQUEST, "The uploaded file was rejected."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "Generation could not be completed. Please retry."),
    AI_GROUNDING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY,
            "Generated content could not be verified against your profile."),
    DOCUMENT_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be rendered.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    /** Safe, client-facing wording. Never contains internal detail. */
    public String defaultMessage() {
        return defaultMessage;
    }
}
