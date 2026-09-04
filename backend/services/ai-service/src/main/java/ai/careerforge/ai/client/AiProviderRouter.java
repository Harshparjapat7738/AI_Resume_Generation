package ai.careerforge.ai.client;

import ai.careerforge.ai.config.AiFallbackProperties;
import ai.careerforge.ai.config.GeminiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one place in the platform that decides Groq-vs-Gemini (ADR-039). The sole
 * {@code @Component} implementing {@link AiChatClient} — every business service still injects
 * that interface exactly as before this record and contains no provider-specific branching at
 * all; {@link GroqClient} and {@link GeminiClient} are plain, concrete-typed collaborators this
 * class holds, never injected anywhere else.
 *
 * <p><strong>Flow:</strong> Groq, always tried first (unless a recent Groq failure already put
 * this operation in cooldown — {@link GroqCooldown}) → on a fallback-eligible failure, for an
 * operation configured for it ({@link AiFallbackProperties}), with Gemini actually usable
 * ({@link GeminiProperties#isUsable()}) → Gemini, tried exactly once → on Gemini failure too, a
 * single normalised {@link AiProviderException}. Groq succeeding never calls Gemini at all.
 *
 * <p><strong>Fallback-eligible</strong> means the failure is one another provider could
 * plausibly succeed at: a temporary rate limit, a timeout, or Groq unavailable/5xx. Never for a
 * deterministic application-side problem (a 4xx we caused) — that would fail identically on
 * Gemini. The one nuanced case is Groq's "this single request alone exceeds the limit" 429: it
 * is fallback-eligible only when the payload is within *our own* configured ceiling (see
 * {@link #PREFLIGHT_CEILINGS}) — if our own payload already exceeds that, this is an internal
 * payload-budget bug, not a capacity problem, and would fail on Gemini the same way.
 */
@Component
public class AiProviderRouter implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(45);

    /** Mirrors each operation's own ai-service fence ceilings (ADR-038) — the sum of every
     *  fenced block plus headroom for the system prompt. Since those fences already truncate
     *  the content before it ever reaches here, this is a defensive assertion in the current
     *  pipeline (should never fire), not a live gate — see this class's own Javadoc. */
    private static final Map<String, Integer> PREFLIGHT_CEILINGS = Map.of(
            "jd-analysis", 43_000,
            "jd-optimization", 24_000);

    private final GroqClient groqClient;
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final AiFallbackProperties fallbackProperties;
    private final GroqCooldown groqCooldown;
    private final MeterRegistry meterRegistry;

    public AiProviderRouter(GroqClient groqClient, GeminiClient geminiClient, GeminiProperties geminiProperties,
                            AiFallbackProperties fallbackProperties, GroqCooldown groqCooldown,
                            MeterRegistry meterRegistry) {
        this.groqClient = groqClient;
        this.geminiClient = geminiClient;
        this.geminiProperties = geminiProperties;
        this.fallbackProperties = fallbackProperties;
        this.groqCooldown = groqCooldown;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public AiChatResult complete(String systemPrompt, String userContent, String operation) {
        return complete(systemPrompt, userContent, operation, null);
    }

    @Override
    public AiChatResult complete(String systemPrompt, String userContent, String operation,
                                 Integer maxCompletionTokensOverride) {
        boolean fallbackAllowed = fallbackProperties.isEnabledFor(operation) && geminiProperties.isUsable();

        if (fallbackAllowed && groqCooldown.isInCooldown(operation)) {
            log.info("operation={} primaryProvider=GROQ finalProvider=GEMINI fallback=true reason=COOLDOWN "
                    + "(Groq recently failed for this operation; skipping straight to Gemini)", operation);
            return callGemini(systemPrompt, userContent, operation, maxCompletionTokensOverride, true);
        }

        try {
            AiChatResult result = groqClient.complete(systemPrompt, userContent, operation, maxCompletionTokensOverride);
            log.info("operation={} primaryProvider=GROQ finalProvider=GROQ fallback=false success=true", operation);
            return result;
        } catch (GroqException groqEx) {
            AiFailureType failureType = classify(groqEx);
            log.warn("operation={} primaryProvider=GROQ success=false failureType={} retryable={}",
                    operation, failureType, groqEx.isRetryable());

            if (groqEx.isRateLimited()) {
                groqCooldown.markUnhealthy(operation, COOLDOWN_TTL);
            }

            if (!fallbackAllowed || !isFallbackEligible(groqEx, userContent, operation)) {
                throw normalize(AiProvider.GROQ, operation, failureType, groqEx.isRetryable(),
                        groqEx.retryAfterSeconds());
            }

            log.info("operation={} primaryProvider=GROQ finalProvider=GEMINI fallback=true reason={}",
                    operation, failureType);
            return callGemini(systemPrompt, userContent, operation, maxCompletionTokensOverride, true);
        }
    }

    private AiChatResult callGemini(String systemPrompt, String userContent, String operation,
                                    Integer maxCompletionTokensOverride, boolean fallback) {
        try {
            AiChatResult result = geminiClient.complete(systemPrompt, userContent, operation, maxCompletionTokensOverride);
            log.info("operation={} finalProvider=GEMINI fallback={} success=true", operation, fallback);
            return result;
        } catch (GeminiException geminiEx) {
            AiFailureType failureType = classifyGemini(geminiEx);
            log.warn("operation={} finalProvider=GEMINI fallback={} success=false failureType={} retryable={}",
                    operation, fallback, failureType, geminiEx.isRetryable());
            throw normalize(AiProvider.GEMINI, operation, failureType, geminiEx.isRetryable(),
                    geminiEx.retryAfterSeconds());
        }
    }

    // ------------------------------------------------------------------ fallback policy ----

    private boolean isFallbackEligible(GroqException ex, String userContent, String operation) {
        if (ex.isTooLarge()) {
            Integer ceiling = PREFLIGHT_CEILINGS.get(operation);
            if (ceiling != null && userContent.length() > ceiling) {
                log.warn("Groq reported 'too large' AND our own payload (chars={}) exceeds the configured "
                                + "ceiling ({}) for operation={} — an internal payload-budget bug, not falling back",
                        userContent.length(), ceiling, operation);
                return false;
            }
            // Our own payload is within our configured ceiling; Gemini's context window is far
            // larger than either ceiling, so this is Groq's own admission math being stricter
            // than expected, not a payload-size problem Gemini would share.
            return true;
        }
        return ex.isRetryable();
    }

    private AiFailureType classify(GroqException ex) {
        if (ex.isTooLarge()) {
            return AiFailureType.QUOTA_EXCEEDED;
        }
        if (ex.isRateLimited()) {
            return AiFailureType.RATE_LIMIT;
        }
        return ex.isRetryable() ? AiFailureType.SERVICE_UNAVAILABLE : AiFailureType.INVALID_REQUEST;
    }

    private AiFailureType classifyGemini(GeminiException ex) {
        if (ex.isTooLarge()) {
            return AiFailureType.QUOTA_EXCEEDED;
        }
        if (ex.isRateLimited()) {
            return AiFailureType.RATE_LIMIT;
        }
        return ex.isRetryable() ? AiFailureType.SERVICE_UNAVAILABLE : AiFailureType.INVALID_REQUEST;
    }

    private AiProviderException normalize(AiProvider provider, String operation, AiFailureType failureType,
                                          boolean retryable, Long retryAfterSeconds) {
        meterRegistry.counter("careerforge.ai.provider_failures", "provider", provider.name(),
                "operation", operation, "failureType", failureType.name()).increment();
        return new AiProviderException(provider, operation, failureType, retryable, retryAfterSeconds,
                "The AI provider could not complete this request (" + failureType + ").");
    }
}
