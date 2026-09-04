package ai.careerforge.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.ai.config.GroqClientConfig;
import ai.careerforge.ai.config.GroqProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Exercises {@link GroqClient}'s retry/rate-limit policy (ADR-038) against a real local HTTP
 * server rather than mocking {@link WebClient} — the behaviour under test lives largely in how
 * headers and status codes drive retry decisions, which a real request/response round trip
 * verifies far more convincingly than a stub.
 */
class GroqClientTest {

    private HttpServer server;
    private AtomicInteger requestCount;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        requestCount = new AtomicInteger(0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GroqClient client(int maxRetries) {
        return client(maxRetries, new SimpleMeterRegistry());
    }

    private GroqClient client(int maxRetries, SimpleMeterRegistry meterRegistry) {
        GroqProperties properties = new GroqProperties(
                "test-key", "http://localhost:" + server.getAddress().getPort(),
                "openai/gpt-oss-120b", 5, 1200, 0.2, maxRetries);
        WebClient webClient = new GroqClientConfig().groqWebClient(properties);
        return new GroqClient(webClient, properties, meterRegistry);
    }

    private static final String OK_BODY = """
            {"model":"openai/gpt-oss-120b","choices":[{"message":{"role":"assistant","content":"{\\"ok\\":true}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""";

    @Test
    void aTooLarge429IsNeverRetried() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 429, "{\"error\":{\"message\":\"Request too large for model, please reduce your prompt\"}}");
        });

        assertThatThrownBy(() -> client(2).complete("system", "user", "op"))
                .isInstanceOf(GroqException.class)
                .satisfies(ex -> {
                    GroqException groq = (GroqException) ex;
                    assertThat(groq.isTooLarge()).isTrue();
                    assertThat(groq.isRetryable()).isFalse();
                });
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void aTemporary429SucceedsAfterOneRetry() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                exchange.getResponseHeaders().add("retry-after", "1");
                respond(exchange, 429, "{\"error\":{\"message\":\"Rate limit reached for requests\"}}");
            } else {
                respond(exchange, 200, OK_BODY);
            }
        });

        AiChatClient.AiChatResult result = client(2).complete("system", "user", "op");

        assertThat(result.content()).isEqualTo("{\"ok\":true}");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("invariant #11: the retry-after header's own value is honoured, not the default exponential backoff")
    void temporary429WaitIsBoundByRetryAfterNotTheExponentialDefault() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                // A retry-after far shorter than the ~2s exponential-backoff default: if this is
                // ignored in favour of the default, the retry would not land until ~2000ms+.
                exchange.getResponseHeaders().add("retry-after", "1");
                respond(exchange, 429, "{\"error\":{\"message\":\"Rate limit reached for requests\"}}");
            } else {
                respond(exchange, 200, OK_BODY);
            }
        });

        long start = System.nanoTime();
        client(2).complete("system", "user", "op");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // retry-after=1 -> ~1000ms; the exponential default's first step is ~2000-2800ms
        // (2000ms base + up to 40% jitter). A wide-but-discriminating window: comfortably above
        // an honoured 1s wait's noise floor, comfortably below where the ignored-default case
        // would land.
        assertThat(elapsedMs).isBetween(900L, 1900L);
    }

    @Test
    void a5xxIsRetriedUpToTheConfiguredMax() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 503, "");
        });

        assertThatThrownBy(() -> client(2).complete("system", "user", "op"))
                .isInstanceOf(GroqException.class);
        // 1 initial attempt + 2 retries = 3 total.
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void aTruncatedResponseIsNeverRetried() throws IOException {
        String truncated = """
                {"model":"openai/gpt-oss-120b","choices":[{"message":{"role":"assistant","content":"{\\"partial"},"finish_reason":"length"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":1000,"total_tokens":1010}}""";
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, truncated);
        });

        assertThatThrownBy(() -> client(2).complete("system", "user", "op"))
                .isInstanceOf(GroqException.class)
                .hasMessageContaining("truncated");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("invariant #14: prompt/completion token usage is captured as metrics, per operation")
    void tokenUsageIsCapturedAsMetrics() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, OK_BODY);
        });
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        client(2, meterRegistry).complete("system", "user", "my-op");

        assertThat(meterRegistry.counter("careerforge.ai.tokens", "operation", "my-op", "kind", "prompt").count())
                .isEqualTo(10.0);
        assertThat(meterRegistry.counter("careerforge.ai.tokens", "operation", "my-op", "kind", "completion").count())
                .isEqualTo(5.0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
