package ai.careerforge.ai.client;

/**
 * Failure talking to Gemini — the fallback provider's counterpart to {@link GroqException}
 * (ADR-039). Kept as its own type, mirroring Groq's, rather than throwing
 * {@link AiProviderException} directly from {@link GeminiClient}: the client reports what
 * *it* saw (provider-specific wire shape); {@link AiProviderRouter} is the one place that
 * decides how a provider-specific failure becomes the normalised, caller-facing exception.
 */
public class GeminiException extends RuntimeException {

    private final boolean retryable;
    private final boolean rateLimited;
    private final boolean tooLarge;
    private final Long retryAfterSeconds;

    public GeminiException(String message, boolean retryable) {
        this(message, retryable, false, false, null, null);
    }

    public GeminiException(String message, boolean retryable, boolean rateLimited, boolean tooLarge,
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

    public boolean isRateLimited() {
        return rateLimited;
    }

    public boolean isTooLarge() {
        return tooLarge;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
