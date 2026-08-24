package ai.careerforge.ai.client;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Minimal provider-health cooldown (ADR-039 §15) — not a distributed circuit breaker, just a
 * short-lived, best-effort flag so a Groq rate-limit condition already observed a moment ago
 * (e.g. by an earlier request, or an earlier attempt in the same repair sequence — see
 * {@code AiGenerationSupport#completeAndValidate}, which calls {@code AiChatClient#complete}
 * a second time for a JSON-repair attempt) doesn't cost a second wasted Groq call before
 * {@link AiProviderRouter} goes straight to Gemini.
 *
 * <p><strong>Best-effort, same degrade-gracefully shape as jd-service's {@code
 * SingleFlightLock}.</strong> ai-service already carries the {@code spring-boot-starter-data-redis}
 * dependency (unused until now); Redis is not running in every local/dev configuration, and a
 * Redis failure here must never block a request — it only ever means "probe Groq again," which
 * is exactly what happens with no cooldown tracking at all.
 */
@Component
public class GroqCooldown {

    private static final Logger log = LoggerFactory.getLogger(GroqCooldown.class);
    private static final String KEY_PREFIX = "ai:groq-cooldown:";

    private final StringRedisTemplate redis;

    public GroqCooldown(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Marks Groq unhealthy for {@code operation} for {@code ttl} — subsequent calls for the
     *  same operation skip straight to Gemini until the cooldown expires, at which point the
     *  key simply disappears and the next call probes Groq again naturally. */
    public void markUnhealthy(String operation, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + operation, "1", ttl);
        } catch (Exception redisUnavailable) {
            log.warn("Could not record Groq cooldown for operation={} (Redis unreachable?): {}",
                    operation, redisUnavailable.getMessage());
        }
    }

    /** @return true if Groq was recently marked unhealthy for this operation and the cooldown
     *          hasn't expired yet — false (probe Groq) on any doubt, including Redis being
     *          unreachable. */
    public boolean isInCooldown(String operation) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + operation));
        } catch (Exception redisUnavailable) {
            log.warn("Could not check Groq cooldown for operation={} (Redis unreachable?): {}",
                    operation, redisUnavailable.getMessage());
            return false;
        }
    }
}
