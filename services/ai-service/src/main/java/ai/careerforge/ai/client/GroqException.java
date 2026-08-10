package ai.careerforge.ai.client;

/**
 * Failure talking to Groq. The message is deliberately generic — Groq error bodies can
 * echo prompt content, which must never reach a client or a log line.
 */
public class GroqException extends RuntimeException {

    private final boolean retryable;

    public GroqException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public GroqException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
