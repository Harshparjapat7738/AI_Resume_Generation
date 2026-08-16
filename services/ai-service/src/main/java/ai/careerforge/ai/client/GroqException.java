package ai.careerforge.ai.client;

/**
 * Failure talking to Groq. The message is deliberately generic — Groq error bodies can
 * echo prompt content, which must never reach a client or a log line.
 */
public class GroqException extends RuntimeException {

    private final boolean retryable;
    private final boolean rateLimited;

    public GroqException(String message, boolean retryable) {
        this(message, retryable, false, null);
    }

    public GroqException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, false, cause);
    }

    public GroqException(String message, boolean retryable, boolean rateLimited, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.rateLimited = rateLimited;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Whether Groq refused this call because the account's own quota was exhausted (HTTP 429),
     * as opposed to being unreachable or erroring.
     *
     * <p>Worth distinguishing because the two need opposite responses and previously looked
     * identical: a 5xx is Groq's problem and retrying in seconds is right, while a 429 is a
     * budget problem that a few seconds of backoff cannot fix — Groq's token allowance refills
     * on a per-minute window, so the caller needs to be told to wait or send less, not told the
     * provider is down.
     */
    public boolean isRateLimited() {
        return rateLimited;
    }
}
