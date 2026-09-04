package ai.careerforge.common.error;

/**
 * Base exception for all expected, client-visible failures.
 *
 * <p>The {@code message} is returned verbatim to the client, so it must never contain
 * credentials, connection strings, file paths, prompts or stack detail.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public ApiException(ErrorCode code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /** Thrown when a resource exists but belongs to another user. */
    public static ApiException notOwned() {
        // Deliberately reported as 404 rather than 403 so that resource IDs cannot be
        // enumerated by observing the difference (BOLA hardening).
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
