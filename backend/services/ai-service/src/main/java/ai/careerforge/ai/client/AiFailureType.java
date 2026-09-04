package ai.careerforge.ai.client;

/**
 * Normalised failure classification (ADR-039), independent of which provider produced it —
 * what {@link AiProviderRouter} uses to decide whether a Groq failure is worth a Gemini
 * fallback attempt, and what ultimately reaches {@link AiProviderException#failureType()}.
 */
public enum AiFailureType {
    /** Temporary per-minute/per-day quota exhaustion — worth falling back to another provider. */
    RATE_LIMIT,
    /** A single request that alone exceeds the provider's limit — never worth retrying
     *  unchanged on the same provider; see {@link AiProviderRouter} for when this is still
     *  eligible for fallback (only when our own pre-flight size check says the payload is
     *  within our configured ceiling). */
    QUOTA_EXCEEDED,
    /** No response within the configured timeout. */
    TIMEOUT,
    /** 5xx / connectivity failure. */
    SERVICE_UNAVAILABLE,
    /** Bad or missing API key — affects every request to that provider identically; never
     *  worth falling back, since a misconfigured provider fails the same way every time. */
    AUTHENTICATION,
    /** A 4xx we caused (bad payload shape) — a code/prompt bug, not a capacity problem. */
    INVALID_REQUEST,
    /** The provider responded, but its content didn't parse as JSON or failed schema
     *  validation even after the one permitted repair attempt. */
    INVALID_RESPONSE
}
