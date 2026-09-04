package ai.careerforge.ai.client;

/**
 * Failure talking to Groq. The message is deliberately generic — Groq error bodies can
 * echo prompt content, which must never reach a client or a log line.
 */
public class GroqException extends RuntimeException {

    private final boolean retryable;
    private final boolean rateLimited;
    private final boolean tooLarge;
    private final Long retryAfterSeconds;

    public GroqException(String message, boolean retryable) {
        this(message, retryable, false, null);
    }

    public GroqException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, false, cause);
    }

    public GroqException(String message, boolean retryable, boolean rateLimited, Throwable cause) {
        this(message, retryable, rateLimited, false, null, cause);
    }

    public GroqException(String message, boolean retryable, boolean rateLimited, boolean tooLarge,
                         Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.rateLimited = rateLimited;
        this.tooLarge = tooLarge;
        this.retryAfterSeconds = retryAfterSeconds;
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

    /**
     * True for Groq's distinct "this one request alone exceeds the entire limit" class of 429
     * (as opposed to "you're temporarily out of budget, others already used it") — see ADR-038.
     * A request in this state cannot succeed by retrying unchanged, at any delay: the payload
     * itself has to shrink. {@link #isRetryable()} is always {@code false} when this is
     * {@code true}.
     */
    public boolean isTooLarge() {
        return tooLarge;
    }

    /**
     * Groq's own {@code retry-after} (seconds), when the 429 response carried one. {@code null}
     * when not applicable (not a 429, or Groq didn't send it) — callers fall back to their own
     * backoff policy in that case.
     */
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
