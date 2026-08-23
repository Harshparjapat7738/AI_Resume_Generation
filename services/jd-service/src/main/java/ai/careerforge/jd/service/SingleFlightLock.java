package ai.careerforge.jd.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Coalesces concurrent identical requests into one computation (ADR-038) — the guard against a
 * user double-clicking "Identify Skill Gaps" (or two tabs open on the same JD) turning into two
 * full analysis+adjudication pipelines racing each other, each spending its own Groq calls
 * against the same tight per-minute budget.
 *
 * <p><strong>Best-effort, not a hard dependency.</strong> Redis already exists in this reactor
 * (jd-service has carried the starter since ADR-002, unused until now) but is not running in
 * every local/dev configuration (see {@code application-nodocker.yml}). If Redis is unreachable,
 * this degrades to "no coalescing" — every caller just computes its own result — rather than
 * failing the request; a double-click wastes an extra Groq call in that mode exactly as it did
 * before this class existed, which is the same behaviour this environment already had, not a
 * regression.
 */
@Component
public class SingleFlightLock {

    private static final Logger log = LoggerFactory.getLogger(SingleFlightLock.class);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public SingleFlightLock(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs {@code compute} under a distributed lock keyed by {@code key}. A caller that loses the
     * race polls {@code checkIfAlreadyDone} (a cheap read of wherever the winner will persist its
     * result — never Redis itself) until it sees the winner's result, up to {@code pollTimeout};
     * if nothing shows up in time, it falls back to computing its own result rather than failing
     * the request outright.
     *
     * @param key              lock key — callers scope this to what must not run twice concurrently
     *                         (e.g. a JD version id)
     * @param lockTtl          safety ceiling so a crashed holder can't wedge the key forever
     * @param pollTimeout      how long a loser waits for the winner's result before giving up and
     *                         computing its own
     * @param checkIfAlreadyDone cheap, side-effect-free read of the shared result store
     * @param compute          the actual work — expected to persist its result somewhere
     *                         {@code checkIfAlreadyDone} can see
     */
    public <T> T withLock(String key, Duration lockTtl, Duration pollTimeout,
            Supplier<Optional<T>> checkIfAlreadyDone, Supplier<T> compute) {
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, lockTtl);
        } catch (Exception redisUnavailable) {
            meterRegistry.counter("careerforge.singleflight.redis_unavailable", "phase", "acquire").increment();
            log.warn("Single-flight lock unavailable (Redis unreachable?) — proceeding without "
                    + "coalescing: {}", redisUnavailable.getMessage());
            return compute.get();
        }

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // Double-checked: a caller who arrives just after a previous holder released the
                // key (its computation already finished and persisted) would otherwise acquire a
                // fresh lock and redundantly recompute — the exact duplicate execution this class
                // exists to prevent, just outside the "someone else is holding it" window rather
                // than inside it. One cheap re-check closes that gap.
                Optional<T> alreadyDone = checkIfAlreadyDone.get();
                if (alreadyDone.isPresent()) {
                    return alreadyDone.get();
                }
                return compute.get();
            } finally {
                releaseIfOwned(key, token);
            }
        }

        // Someone else is already computing this — wait for their result instead of racing them.
        long deadline = System.currentTimeMillis() + pollTimeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<T> existing = checkIfAlreadyDone.get();
            if (existing.isPresent()) {
                return existing.get();
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("Single-flight: gave up waiting for the in-flight computation for key={} — "
                + "computing independently", key);
        return compute.get();
    }

    private void releaseIfOwned(String key, String token) {
        try {
            String current = redis.opsForValue().get(key);
            if (token.equals(current)) {
                redis.delete(key);
            }
        } catch (Exception redisUnavailable) {
            meterRegistry.counter("careerforge.singleflight.redis_unavailable", "phase", "release").increment();
            log.warn("Could not release single-flight lock key={}: {}", key, redisUnavailable.getMessage());
        }
    }
}
