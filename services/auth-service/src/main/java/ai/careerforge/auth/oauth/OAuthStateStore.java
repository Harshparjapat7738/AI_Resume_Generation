package ai.careerforge.auth.oauth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Binds an OAuth {@code state} to the PKCE {@code code_verifier} that started the flow, in
 * Redis rather than an HTTP session — this service is stateless (SecurityConfig) and runs
 * behind a load balancer, so a server-affinity-dependent session would break the flow the
 * moment two instances are involved.
 *
 * <p>Single-use by construction: {@link #consume(String)} is a get-and-delete, so a replayed
 * {@code state} (a stale bookmark, a replayed callback) always fails — matching
 * docs/EXTERNAL_APIS.md: "state is single-use... a mismatch aborts with AUTH_REQUIRED".
 */
@Component
class OAuthStateStore {

    private static final String KEY_PREFIX = "oauth:google:state:";
    /** Generous enough for a real consent-screen interaction, short enough that an
     *  abandoned flow doesn't linger. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    OAuthStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    void store(String state, String codeVerifier) {
        redis.opsForValue().set(KEY_PREFIX + state, codeVerifier, TTL);
    }

    /** Returns the associated verifier, or {@code null} if the state is unknown, expired, or
     *  already consumed. Removes it either way — never usable twice. */
    String consume(String state) {
        return redis.opsForValue().getAndDelete(KEY_PREFIX + state);
    }
}
