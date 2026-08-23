package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Single-flight request coalescing (ADR-038) — the guard against a double-click racing two
 *  full pipelines against the same tight per-minute Groq budget. */
class SingleFlightLockTest {

    @Test
    @DisplayName("acquiring the lock runs compute exactly once, then releases its own token")
    void acquiringTheLockRunsComputeOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        // Echo back whatever token was set, the way a real Redis SET/GET pair would — proves
        // release only happens because the lock recognises its own token, not unconditionally.
        var storedToken = new java.util.concurrent.atomic.AtomicReference<String>();
        when(values.setIfAbsent(eq("key"), any(), any(Duration.class))).thenAnswer(inv -> {
            storedToken.set(inv.getArgument(1));
            return true;
        });
        when(values.get("key")).thenAnswer(inv -> storedToken.get());
        SingleFlightLock lock = new SingleFlightLock(redis);
        AtomicInteger computeCalls = new AtomicInteger();

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(1),
                Optional::empty, () -> {
                    computeCalls.incrementAndGet();
                    return "computed";
                });

        assertThat(result).isEqualTo("computed");
        assertThat(computeCalls.get()).isEqualTo(1);
        verify(redis).delete("key");
    }

    @Test
    @DisplayName("losing the race polls for the winner's result instead of computing independently")
    void losingTheRacePollsForTheWinnersResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("key"), any(), any(Duration.class))).thenReturn(false);
        SingleFlightLock lock = new SingleFlightLock(redis);
        AtomicInteger computeCalls = new AtomicInteger();
        AtomicInteger checkCalls = new AtomicInteger();

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(2),
                () -> checkCalls.incrementAndGet() >= 2 ? Optional.of("winner's result") : Optional.empty(),
                () -> {
                    computeCalls.incrementAndGet();
                    return "computed independently";
                });

        assertThat(result).isEqualTo("winner's result");
        assertThat(computeCalls.get()).isZero();
    }

    @Test
    @DisplayName("giving up waiting falls back to computing independently rather than failing the request")
    void givesUpAndComputesIndependentlyIfTheWinnerNeverFinishes() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("key"), any(), any(Duration.class))).thenReturn(false);
        SingleFlightLock lock = new SingleFlightLock(redis);

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofMillis(500),
                Optional::empty, () -> "computed independently");

        assertThat(result).isEqualTo("computed independently");
    }

    @Test
    @DisplayName("Redis being unreachable degrades to no coalescing rather than failing the request")
    void redisUnavailableDegradesToNoCoalescing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        SingleFlightLock lock = new SingleFlightLock(redis);
        AtomicInteger computeCalls = new AtomicInteger();

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(1),
                Optional::empty, () -> {
                    computeCalls.incrementAndGet();
                    return "computed";
                });

        assertThat(result).isEqualTo("computed");
        assertThat(computeCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the lock is only released by whoever still owns the token")
    void lockIsOnlyReleasedByItsOwner() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("key"), any(), any(Duration.class))).thenReturn(true);
        // Simulate the key having been taken over by someone else (e.g. TTL expiry + another
        // holder) by the time release runs.
        when(values.get("key")).thenReturn("someone-elses-token");
        SingleFlightLock lock = new SingleFlightLock(redis);

        lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(1), Optional::empty, () -> "computed");

        verify(redis, never()).delete("key");
    }
}
