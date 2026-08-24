package ai.careerforge.ai.client;

import ai.careerforge.ai.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * The only class in the platform that talks to Gemini for JSON generation (ADR-039) — the
 * fallback provider {@link AiProviderRouter} calls when Groq fails in a way another provider
 * could plausibly succeed at, for the handful of operations configured for fallback.
 *
 * <p><strong>Single-shot, deliberately</strong> — no internal retry loop. This is already the
 * fallback path; retrying it would spend more of Gemini's own limited quota (Gemini has its own
 * RPM/TPM/RPD limits — ADR-039 §16, never assume it is unlimited just because it is the backup)
 * for a call whose only remaining escalation, per the router's policy, is a controlled failure
 * anyway.
 *
 * <p><strong>Structured output</strong> (ADR-039 §7): every request sets {@code
 * responseMimeType=application/json} plus a hand-written {@code responseSchema} mirroring the
 * exact shape of the canonical JSON schema the operation's Groq output is validated against —
 * never "ask nicely for JSON in the prompt." The response is still run through the identical
 * {@code SchemaValidator}/{@code AiGenerationSupport} pipeline afterward; the structured-output
 * hint reduces how often that validation has anything to reject, it does not replace it.
 *
 * <p><strong>{@code thinkingConfig} is not optional</strong> — live-verified against the real
 * API (ADR-039): the configured default model reasons before answering, and that reasoning is
 * billed against the same output-token budget as the visible JSON. A trivial prompt with no
 * {@code thinkingConfig} spent ~290 hidden tokens before writing 5 visible ones and truncated
 * (finish reason {@code MAX_TOKENS}, empty content) once a small budget made that arithmetic
 * fail; the model rejects {@code thinkingBudget:0} outright. {@link GeminiMessages.ThinkingConfig
 * #low()} (thinking level {@code LOW}) is what actually works — verified live to bring hidden
 * token spend to effectively zero without raising {@code maxOutputTokens} at all. <strong>Open
 * risk, also observed live and not yet resolved</strong>: on the one real jd-analysis prompt
 * tested end-to-end, the low-thinking response correctly extracted keywords but returned an
 * empty {@code requirements} array despite the job description containing unambiguous "Required:
 * ..." lines — whether this is a one-off or a systematic quality cost of {@code LOW} specifically
 * for the more inferential jd-analysis extraction (as opposed to jd-optimization adjudication,
 * a comparatively shallower per-item judgement) is not established from a single sample and
 * needs a real evaluation pass before this fallback is trusted in production for that operation.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String TOO_LARGE_MARKER = "too large";
    /** Operation -&gt; classpath resource for this operation's Gemini structured-output schema.
     *  Only the two operations ADR-039 enables fallback for have one; anything else falls back
     *  to plain {@code responseMimeType=application/json} with no schema hint. */
    private static final Map<String, String> RESPONSE_SCHEMAS = Map.of(
            "jd-analysis", "schemas/gemini/jd-analysis.gemini-schema.json",
            "jd-optimization", "schemas/gemini/jd-optimization.gemini-schema.json");

    private final WebClient webClient;
    private final GeminiProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> schemaCache = new ConcurrentHashMap<>();

    public GeminiClient(WebClient geminiWebClient, GeminiProperties properties,
                        MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.webClient = geminiWebClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    public AiChatClient.AiChatResult complete(String systemPrompt, String userContent, String operation,
                                              Integer maxCompletionTokensOverride) {
        if (!properties.isUsable()) {
            throw new GeminiException("Gemini is not configured (no API key / disabled)", false);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        int maxOutputTokens = maxCompletionTokensOverride != null
                ? maxCompletionTokensOverride : properties.maxOutputTokens();

        GeminiMessages.GenerateContentRequest request = new GeminiMessages.GenerateContentRequest(
                GeminiMessages.SystemInstruction.of(systemPrompt),
                java.util.List.of(GeminiMessages.Content.user(userContent)),
                new GeminiMessages.GenerationConfig(0.2, maxOutputTokens, "application/json",
                        responseSchemaFor(operation), GeminiMessages.ThinkingConfig.low()));

        String uri = "/v1beta/models/" + properties.model() + ":generateContent";

        try {
            GeminiMessages.GenerateContentResponse response = webClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())
                    .bodyValue(request)
                    .exchangeToMono(clientResponse -> handleResponse(clientResponse, operation))
                    .block(Duration.ofSeconds(properties.timeoutSeconds() + 5L));

            if (response == null || response.firstText() == null) {
                throw new GeminiException("Gemini returned no content", true);
            }
            if ("MAX_TOKENS".equals(response.firstFinishReason())) {
                // Same reasoning as Groq's finish_reason=length (ADR-038): truncated output is
                // invalid JSON, and retrying unchanged truncates identically.
                throw new GeminiException("Gemini response was truncated at the token limit", false);
            }

            recordUsage(operation, response);
            return new AiChatClient.AiChatResult(response.firstText(), properties.model(),
                    response.usageMetadata() == null ? 0 : response.usageMetadata().totalTokenCount(),
                    AiProvider.GEMINI);

        } catch (GeminiException ex) {
            meterRegistry.counter("careerforge.ai.failures", "provider", "gemini", "operation", operation,
                    "retryable", String.valueOf(ex.isRetryable())).increment();
            log.warn("Gemini call failed operation={} retryable={} reason={}",
                    operation, ex.isRetryable(), ex.getMessage());
            throw ex;
        } finally {
            sample.stop(meterRegistry.timer("careerforge.ai.latency", "provider", "gemini", "operation", operation));
        }
    }

    // ------------------------------------------------------------------ response handling ----

    private Mono<GeminiMessages.GenerateContentResponse> handleResponse(ClientResponse clientResponse, String operation) {
        HttpStatusCode status = clientResponse.statusCode();
        logRateLimitHeaders(operation, status.value(), clientResponse.headers().asHttpHeaders());

        if (status.value() == 429) {
            Long retryAfter = retryAfterSeconds(clientResponse.headers().asHttpHeaders());
            return clientResponse.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(body -> Mono.error(classify429(body, retryAfter)));
        }
        if (status.value() == 401 || status.value() == 403) {
            return clientResponse.releaseBody().then(Mono.error(
                    new GeminiException("Gemini rejected the API key", false)));
        }
        if (status.is5xxServerError()) {
            return Mono.error(new GeminiException("Gemini returned " + status.value(), true));
        }
        if (status.is4xxClientError()) {
            return clientResponse.releaseBody().then(Mono.error(
                    new GeminiException("Gemini rejected the request: " + status.value(), false)));
        }
        return clientResponse.bodyToMono(GeminiMessages.GenerateContentResponse.class);
    }

    /** Mirrors {@code GroqClient}'s classify429 (ADR-038/039): Gemini's {@code RESOURCE_EXHAUSTED}
     *  status covers both a temporary quota exhaustion and a single-request-too-large case;
     *  the message wording is the only way to tell them apart, same as Groq. */
    private GeminiException classify429(String body, Long retryAfter) {
        boolean tooLarge = body != null && body.toLowerCase(Locale.ROOT).contains(TOO_LARGE_MARKER);
        if (tooLarge) {
            return new GeminiException("Gemini rejected this request: it alone exceeds the account's limit",
                    false, true, true, retryAfter, null);
        }
        return new GeminiException("Gemini rate limit reached", true, true, false, retryAfter, null);
    }

    private Long retryAfterSeconds(HttpHeaders headers) {
        String value = headers.getFirst("retry-after");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void logRateLimitHeaders(String operation, int status, HttpHeaders headers) {
        String remainingRequests = headers.getFirst("x-ratelimit-remaining-requests");
        String remainingTokens = headers.getFirst("x-ratelimit-remaining-tokens");
        if (remainingRequests != null || remainingTokens != null) {
            log.info("Gemini response operation={} status={} remainingRequests={} remainingTokens={}",
                    operation, status, remainingRequests, remainingTokens);
        }
    }

    private void recordUsage(String operation, GeminiMessages.GenerateContentResponse response) {
        if (response.usageMetadata() == null) {
            return;
        }
        Integer prompt = response.usageMetadata().promptTokenCount();
        Integer completion = response.usageMetadata().candidatesTokenCount();
        meterRegistry.counter("careerforge.ai.tokens", "provider", "gemini", "operation", operation, "kind", "prompt")
                .increment(prompt == null ? 0 : prompt);
        meterRegistry.counter("careerforge.ai.tokens", "provider", "gemini", "operation", operation, "kind", "completion")
                .increment(completion == null ? 0 : completion);
        log.info("Gemini call ok operation={} model={} promptTokens={} completionTokens={} finishReason={}",
                operation, properties.model(), prompt, completion, response.firstFinishReason());
    }

    // ------------------------------------------------------------------ structured output ----

    private JsonNode responseSchemaFor(String operation) {
        String resourcePath = RESPONSE_SCHEMAS.get(operation);
        if (resourcePath == null) {
            return null;
        }
        return schemaCache.computeIfAbsent(resourcePath, path -> {
            try (var in = new ClassPathResource(path).getInputStream()) {
                return objectMapper.readTree(in);
            } catch (IOException ex) {
                throw new UncheckedIOException("Missing Gemini structured-output schema: " + path, ex);
            }
        });
    }
}
