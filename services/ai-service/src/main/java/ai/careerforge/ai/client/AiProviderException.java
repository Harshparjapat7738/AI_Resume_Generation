package ai.careerforge.ai.client;

/**
 * The one exception type that ever escapes {@link AiProviderRouter} (ADR-039) — a normalised,
 * provider-tagged failure, regardless of whether it came directly from Groq, from Gemini after
 * a fallback attempt, or from Groq with no fallback attempted at all (fallback not configured
 * for this operation, Gemini not configured, or the failure wasn't fallback-eligible). Callers
 * ({@code AiController}) need exactly one exception type to translate into the platform error
 * envelope, never a provider-specific one — that coupling is exactly what this class removes.
 *
 * @param provider    the provider whose failure this ultimately reports — the last one tried
 * @param operation   metric/log tag, e.g. {@code jd-analysis}
 * @param failureType normalised classification, independent of provider wire format
 * @param retryable   whether the underlying failure was itself retryable (informational; the
 *                    router has already exhausted whatever retry/fallback it was going to do
 *                    by the time this is thrown)
 * @param retryAfterSeconds the last provider's own retry-after, if any
 * @param message     safe, generic — never echoes prompt or response content
 */
public class AiProviderException extends RuntimeException {

    private final AiProvider provider;
    private final String operation;
    private final AiFailureType failureType;
    private final boolean retryable;
    private final Long retryAfterSeconds;

    public AiProviderException(AiProvider provider, String operation, AiFailureType failureType,
                               boolean retryable, Long retryAfterSeconds, String message) {
        super(message);
        this.provider = provider;
        this.operation = operation;
        this.failureType = failureType;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public AiProvider provider() {
        return provider;
    }

    public String operation() {
        return operation;
    }

    public AiFailureType failureType() {
        return failureType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isRateLimited() {
        return failureType == AiFailureType.RATE_LIMIT || failureType == AiFailureType.QUOTA_EXCEEDED;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
