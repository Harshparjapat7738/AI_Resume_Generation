package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        SingleFlightLock lock = new SingleFlightLock(redis, new SimpleMeterRegistry());
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
        SingleFlightLock lock = new SingleFlightLock(redis, new SimpleMeterRegistry());
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
        SingleFlightLock lock = new SingleFlightLock(redis, new SimpleMeterRegistry());

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofMillis(500),
                Optional::empty, () -> "computed independently");

        assertThat(result).isEqualTo("computed independently");
    }

    @Test
    @DisplayName("Redis being unreachable degrades to no coalescing rather than failing the request, and is counted")
    void redisUnavailableDegradesToNoCoalescingAndIsObservable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SingleFlightLock lock = new SingleFlightLock(redis, meterRegistry);
        AtomicInteger computeCalls = new AtomicInteger();

        String result = lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(1),
                Optional::empty, () -> {
                    computeCalls.incrementAndGet();
                    return "computed";
                });

        assertThat(result).isEqualTo("computed");
        assertThat(computeCalls.get()).isEqualTo(1);
        // Invariant #9: Redis failure must be observable, not just silently swallowed.
        assertThat(meterRegistry.counter("careerforge.singleflight.redis_unavailable", "phase", "acquire").count())
                .isEqualTo(1.0);
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
        SingleFlightLock lock = new SingleFlightLock(redis, new SimpleMeterRegistry());

        lock.withLock("key", Duration.ofSeconds(30), Duration.ofSeconds(1), Optional::empty, () -> "computed");

        verify(redis, never()).delete("key");
    }

    @Test
    @DisplayName("invariant #8: under real concurrent contention against a working lock backend, "
            + "compute runs exactly once for N racing callers")
    void realConcurrencyAgainstAWorkingLockRunsComputeExactlyOnce() throws Exception {
        // A minimal, thread-safe fake of the two Redis operations this class actually uses —
        // "Redis available" modelled as a real, working distributed lock, not a sequential mock
        // script. This is what actually exercises thread-safety, which a scripted true/false
        // stub (as in the tests above) cannot.
        ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), any(), any(Duration.class))).thenAnswer(inv ->
                store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        when(values.get(any())).thenAnswer(inv -> store.get((String) inv.getArgument(0)));
        org.mockito.Mockito.doAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return null;
        }).when(redis).delete(org.mockito.ArgumentMatchers.anyString());

        SingleFlightLock lock = new SingleFlightLock(redis, new SimpleMeterRegistry());
        AtomicInteger computeCalls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> sharedResult = new java.util.concurrent.atomic.AtomicReference<>();
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return lock.withLock("shared-key", Duration.ofSeconds(10), Duration.ofSeconds(5),
                        () -> Optional.ofNullable(sharedResult.get()),
                        () -> {
                            int n = computeCalls.incrementAndGet();
                            String value = "computed-" + n;
                            sharedResult.set(value);
                            return value;
                        });
            }));
        }
        ready.await(2, TimeUnit.SECONDS);
        go.countDown();

        List<String> results = new java.util.ArrayList<>();
        for (var future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(computeCalls.get()).isEqualTo(1);
        assertThat(results).allMatch(r -> r.equals("computed-1"));
    }
}
