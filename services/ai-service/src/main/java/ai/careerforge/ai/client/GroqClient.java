package ai.careerforge.ai.client;

import ai.careerforge.ai.config.GroqProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * The only class in the platform that talks to Groq — the sole {@link AiChatClient}
 * implementation (see that interface's Javadoc for why the interface exists and what it
 * deliberately does not add), and therefore the provider behind every JSON/content-generation
 * operation: JD Analysis, Evidence Selection, JD Optimization and Email Content (ADR-033).
 *
 * <p>Not marked {@code @Primary} — there is nothing to disambiguate from. Gemini was removed
 * entirely by ADR-033, so this is the only {@code AiChatClient} bean; every call site injects
 * the interface unqualified and resolves here.
 *
 * <p><strong>Rate-limit awareness (ADR-038).</strong> Every response — success or failure — has
 * its {@code x-ratelimit-*} and {@code retry-after} headers captured and logged, never silently
 * discarded as before. A 429 is classified before any retry decision is made: Groq's own error
 * body distinguishes "this single request alone exceeds the whole limit" (unrecoverable by
 * retrying, ever — see {@link GroqException#isTooLarge()}) from "the account is temporarily out
 * of budget" (worth exactly one delayed retry, honouring {@code retry-after} when Groq sent one).
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Responses are constrained to a JSON object, so instruction-following prose emitted
 *       by a prompt-injection attempt cannot survive parsing.</li>
 *   <li>Only a genuinely temporary 429 and 5xx are retried, with backoff. A "too large" 429, a
 *       4xx we caused, and a truncated ({@code finish_reason=length}) response are never
 *       retried — none of them can succeed unchanged.</li>
 *   <li>Neither the request nor the response body is ever logged. Only model, latency,
 *       token counts, finish reason, rate-limit headers and status are recorded.</li>
 * </ul>
 */
@Component
public class GroqClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String CHAT_COMPLETIONS = "/chat/completions";
    /** Groq's 429 body echoes wording like "Request too large for ..." for the unrecoverable
     *  class specifically — distinct from "Rate limit reached ..." for a temporary one. */
    private static final String TOO_LARGE_MARKER = "too large";

    private final WebClient webClient;
    private final GroqProperties properties;
    private final MeterRegistry meterRegistry;

    public GroqClient(WebClient groqWebClient, GroqProperties properties,
                      MeterRegistry meterRegistry) {
        this.webClient = groqWebClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public AiChatResult complete(String systemPrompt, String userContent, String operation) {
        return complete(systemPrompt, userContent, operation, null);
    }

    /**
     * @param maxCompletionTokensOverride reserves this many completion tokens instead of the
     *                                    configured default (ADR-038); {@code null} keeps the
     *                                    configured default
     */
    @Override
    public AiChatResult complete(String systemPrompt, String userContent, String operation,
                                 Integer maxCompletionTokensOverride) {
        Timer.Sample sample = Timer.start(meterRegistry);
        int maxCompletionTokens = maxCompletionTokensOverride != null
                ? maxCompletionTokensOverride : properties.maxOutputTokens();

        GroqMessages.ChatRequest request = new GroqMessages.ChatRequest(
                properties.model(),
                List.of(GroqMessages.Message.system(systemPrompt),
                        GroqMessages.Message.user(userContent)),
                properties.temperature(),
                maxCompletionTokens,
                GroqMessages.ResponseFormat.jsonObject(),
                // Suppress gpt-oss's reasoning trace in the response — see ChatRequest's own
                // comment on include_reasoning. A no-op for a non-reasoning model.
                Boolean.FALSE);

        try {
            GroqMessages.ChatResponse response = webClient.post()
                    .uri(CHAT_COMPLETIONS)
                    .bodyValue(request)
                    .exchangeToMono(clientResponse -> handleResponse(clientResponse, operation))
                    .retryWhen(Retry.from(signals -> signals.flatMap(signal -> {
                        GroqException ex = asGroqException(signal.failure());
                        if (!isRetryable(ex)) {
                            return Mono.error(signal.failure());
                        }
                        Duration delay = backoffDelay(ex, signal.totalRetries());
                        if (signal.totalRetries() >= properties.maxRetries()) {
                            boolean rateLimited = ex != null && ex.isRateLimited();
                            String reason = rateLimited
                                    ? "Groq rate limit (429) still exceeded after " + signal.totalRetries()
                                            + " retries — the account's per-minute request/token budget is "
                                            + "exhausted, not a Groq outage"
                                    : "Groq unavailable after " + signal.totalRetries() + " retries";
                            return Mono.error(new GroqException(reason, true, rateLimited, false, null,
                                    signal.failure()));
                        }
                        return Mono.delay(delay);
                    })))
                    .block(Duration.ofSeconds(properties.timeoutSeconds() + 5L));

            if (response == null || response.firstContent() == null) {
                throw new GroqException("Groq returned no content", true);
            }
            if ("length".equals(response.firstFinishReason())) {
                // Truncated output is invalid JSON; failing loudly beats persisting a
                // half-written result. Never worth retrying unchanged — it will truncate
                // identically (ADR-038).
                throw new GroqException("Groq response was truncated at the token limit", false);
            }

            recordUsage(operation, response);
            return new AiChatResult(response.firstContent(), response.model(),
                    response.usage() == null ? 0 : response.usage().total_tokens());

        } catch (GroqException ex) {
            meterRegistry.counter("careerforge.ai.failures", "operation", operation,
                    "retryable", String.valueOf(ex.isRetryable()),
                    "tooLarge", String.valueOf(ex.isTooLarge())).increment();
            log.warn("Groq call failed operation={} retryable={} tooLarge={} reason={}",
                    operation, ex.isRetryable(), ex.isTooLarge(), ex.getMessage());
            throw ex;
        } finally {
            sample.stop(meterRegistry.timer("careerforge.ai.latency", "operation", operation));
        }
    }

    // ------------------------------------------------------------------ response handling ----

    private Mono<GroqMessages.ChatResponse> handleResponse(ClientResponse clientResponse, String operation) {
        HttpStatusCode status = clientResponse.statusCode();
        RateLimitHeaders headers = RateLimitHeaders.from(clientResponse.headers().asHttpHeaders());
        logHeaders(operation, status.value(), headers);

        if (status.value() == 429) {
            return clientResponse.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(body -> Mono.error(classify429(body, headers)));
        }
        if (status.is5xxServerError()) {
            return Mono.error(new GroqException("Groq returned " + status.value(), true));
        }
        if (status.is4xxClientError()) {
            return clientResponse.releaseBody().then(Mono.error(
                    new GroqException("Groq rejected the request: " + status.value(), false)));
        }
        return clientResponse.bodyToMono(GroqMessages.ChatResponse.class);
    }

    /**
     * Distinguishes Groq's two 429 shapes (ADR-038): a single request that alone exceeds the
     * whole per-minute limit (never retryable, at any delay) from a temporary budget exhaustion
     * (worth one delayed retry, honouring {@code retry-after} when present). Never logs the body
     * itself — it can echo prompt content — only which class it matched and the numbers Groq
     * reported, none of which are user content.
     */
    private GroqException classify429(String body, RateLimitHeaders headers) {
        boolean tooLarge = body != null && body.toLowerCase(java.util.Locale.ROOT).contains(TOO_LARGE_MARKER);
        if (tooLarge) {
            log.warn("Groq 429: request too large for the account's rate limit — this cannot "
                    + "succeed by retrying; the payload must shrink");
            return new GroqException(
                    "Groq rejected this request: it alone exceeds the account's rate limit",
                    false, true, true, headers.retryAfterSeconds(), null);
        }
        return new GroqException("Groq returned 429", true, true, false, headers.retryAfterSeconds(), null);
    }

    private void logHeaders(String operation, int status, RateLimitHeaders headers) {
        if (headers.hasAny()) {
            log.info("Groq response operation={} status={} retryAfter={} "
                            + "limitRequests={} remainingRequests={} resetRequests={} "
                            + "limitTokens={} remainingTokens={} resetTokens={}",
                    operation, status, headers.retryAfterSeconds(),
                    headers.limitRequests(), headers.remainingRequests(), headers.resetRequests(),
                    headers.limitTokens(), headers.remainingTokens(), headers.resetTokens());
        }
    }

    // ------------------------------------------------------------------ retry policy ----

    private GroqException asGroqException(Throwable throwable) {
        return throwable instanceof GroqException groq ? groq : null;
    }

    /** 5xx and a temporary (non-too-large) 429 are retryable; a too-large 429 and any 4xx we
     *  caused are not — no delay can fix those. */
    private boolean isRetryable(GroqException ex) {
        if (ex == null) {
            // Connection resets and timeouts are worth one more attempt.
            return true;
        }
        return ex.isRetryable() && !ex.isTooLarge();
    }

    /** Honours Groq's own {@code retry-after} for a rate-limited retry; falls back to
     *  exponential-with-jitter for everything else (5xx, transport errors). */
    private Duration backoffDelay(GroqException ex, long attemptIndex) {
        if (ex != null && ex.isRateLimited() && ex.retryAfterSeconds() != null) {
            return Duration.ofSeconds(Math.max(1, ex.retryAfterSeconds()));
        }
        long base = 2_000L * (1L << Math.min(attemptIndex, 4));
        long jitter = (long) (base * 0.4 * Math.random());
        return Duration.ofMillis(Math.min(base + jitter, 20_000L));
    }

    private void recordUsage(String operation, GroqMessages.ChatResponse response) {
        if (response.usage() == null) {
            return;
        }
        meterRegistry.counter("careerforge.ai.tokens", "operation", operation, "kind", "prompt")
                .increment(response.usage().prompt_tokens());
        meterRegistry.counter("careerforge.ai.tokens", "operation", operation, "kind", "completion")
                .increment(response.usage().completion_tokens());
        log.info("Groq call ok operation={} model={} promptTokens={} completionTokens={} finishReason={}",
                operation, response.model(),
                response.usage().prompt_tokens(), response.usage().completion_tokens(),
                response.firstFinishReason());
    }

    /** One response's rate-limit headers, parsed once. Every field is nullable — Groq only
     *  sends the ones relevant to the response it just gave. */
    private record RateLimitHeaders(
            Long retryAfterSeconds, Long limitRequests, Long remainingRequests, String resetRequests,
            Long limitTokens, Long remainingTokens, String resetTokens) {

        static RateLimitHeaders from(HttpHeaders headers) {
            return new RateLimitHeaders(
                    asLong(headers.getFirst("retry-after")),
                    asLong(headers.getFirst("x-ratelimit-limit-requests")),
                    asLong(headers.getFirst("x-ratelimit-remaining-requests")),
                    headers.getFirst("x-ratelimit-reset-requests"),
                    asLong(headers.getFirst("x-ratelimit-limit-tokens")),
                    asLong(headers.getFirst("x-ratelimit-remaining-tokens")),
                    headers.getFirst("x-ratelimit-reset-tokens"));
        }

        boolean hasAny() {
            return retryAfterSeconds != null || limitRequests != null || remainingRequests != null
                    || limitTokens != null || remainingTokens != null;
        }

        private static Long asLong(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
