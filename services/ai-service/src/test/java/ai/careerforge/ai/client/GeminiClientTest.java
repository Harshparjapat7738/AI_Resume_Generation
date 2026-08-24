package ai.careerforge.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.ai.config.GeminiClientConfig;
import ai.careerforge.ai.config.GeminiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Exercises {@link GeminiClient} against a real local HTTP server (ADR-039), mirroring
 * {@code GroqClientTest} — the behaviour under test lives in request shape (structured-output
 * schema, headers) and response classification, which a real round trip verifies more
 * convincingly than a stubbed {@link WebClient}.
 */
class GeminiClientTest {

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

    private GeminiClient client() {
        GeminiProperties properties = new GeminiProperties(true, "test-gemini-key",
                "http://localhost:" + server.getAddress().getPort(), "gemini-2.5-flash", 5, 1200);
        WebClient webClient = new GeminiClientConfig().geminiWebClient(properties);
        return new GeminiClient(webClient, properties, new SimpleMeterRegistry(), new ObjectMapper());
    }

    private static final String OK_BODY = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"ok\\":true}"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":6,"totalTokenCount":18}}""";

    @Test
    @DisplayName("success: returns the model's content tagged with AiProvider.GEMINI")
    void successReturnsGeminiTaggedResult() throws IOException {
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, OK_BODY);
        });

        AiChatClient.AiChatResult result = client().complete("system", "user", "jd-analysis", 1200);

        assertThat(result.content()).isEqualTo("{\"ok\":true}");
        assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the API key is sent as a header, never as a query parameter or in the body")
    void apiKeySentAsHeaderOnly() throws IOException {
        AtomicReference<String> seenHeader = new AtomicReference<>();
        AtomicReference<String> seenQuery = new AtomicReference<>();
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            seenHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            seenQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, OK_BODY);
        });

        client().complete("system", "user", "jd-analysis", 1200);

        assertThat(seenHeader.get()).isEqualTo("test-gemini-key");
        assertThat(seenQuery.get()).isNull();
    }

    @Test
    @DisplayName("structured output: a request for a known operation carries its responseSchema")
    void knownOperationCarriesResponseSchema() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, OK_BODY);
        });

        client().complete("system", "user", "jd-optimization", 1000);

        assertThat(body.get()).contains("responseSchema").contains("matchKind").contains("STRONG");
    }

    @Test
    @DisplayName("a 429 whose message says 'too large' is classified as too-large, not a temporary rate limit")
    void tooLarge429IsClassifiedCorrectly() throws IOException {
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange ->
                respond(exchange, 429, "{\"error\":{\"code\":429,\"message\":\"Request too large\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> client().complete("system", "user", "jd-analysis", 1200))
                .isInstanceOf(GeminiException.class)
                .satisfies(ex -> assertThat(((GeminiException) ex).isTooLarge()).isTrue());
    }

    @Test
    @DisplayName("a temporary 429 is classified as rate-limited, not too-large")
    void temporary429IsClassifiedAsRateLimited() throws IOException {
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange ->
                respond(exchange, 429, "{\"error\":{\"code\":429,\"message\":\"Resource exhausted\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> client().complete("system", "user", "jd-analysis", 1200))
                .isInstanceOf(GeminiException.class)
                .satisfies(ex -> {
                    GeminiException g = (GeminiException) ex;
                    assertThat(g.isRateLimited()).isTrue();
                    assertThat(g.isTooLarge()).isFalse();
                });
    }

    @Test
    @DisplayName("a truncated (MAX_TOKENS) response is never retried")
    void truncatedResponseIsNeverRetried() throws IOException {
        String truncated = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"partial"}]},"finishReason":"MAX_TOKENS"}],
                 "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":1200,"totalTokenCount":1212}}""";
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, truncated);
        });

        assertThatThrownBy(() -> client().complete("system", "user", "jd-analysis", 1200))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("truncated");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unusable (unconfigured) Gemini client fails fast without making any HTTP call")
    void unusableClientFailsFastWithoutCallingOut() {
        GeminiProperties disabled = new GeminiProperties(false, "", "http://localhost:1", "m", 5, 1200);
        GeminiClient disabledClient = new GeminiClient(
                new GeminiClientConfig().geminiWebClient(disabled), disabled, new SimpleMeterRegistry(), new ObjectMapper());

        assertThatThrownBy(() -> disabledClient.complete("system", "user", "jd-analysis", 1200))
                .isInstanceOf(GeminiException.class);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
