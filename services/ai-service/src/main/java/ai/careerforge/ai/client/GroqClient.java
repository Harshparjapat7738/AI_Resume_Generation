package ai.careerforge.ai.client;

import ai.careerforge.ai.config.GroqProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
 * <p>Guarantees:
 * <ul>
 *   <li>Responses are constrained to a JSON object, so instruction-following prose emitted
 *       by a prompt-injection attempt cannot survive parsing.</li>
 *   <li>Only 429 and 5xx are retried, with exponential backoff and jitter. A 4xx we caused
 *       is never retried — it would burn quota to fail identically.</li>
 *   <li>Neither the request nor the response body is ever logged. Only model, latency,
 *       token counts and status are recorded.</li>
 * </ul>
 */
@Component
public class GroqClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String CHAT_COMPLETIONS = "/chat/completions";

    private final WebClient webClient;
    private final GroqProperties properties;
    private final MeterRegistry meterRegistry;

    public GroqClient(WebClient groqWebClient, GroqProperties properties,
                      MeterRegistry meterRegistry) {
        this.webClient = groqWebClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs one chat completion and returns the raw JSON string content.
     *
     * @param systemPrompt the versioned instruction block; never user-supplied
     * @param userContent  the data block, containing untrusted content already delimited
     *                     and labelled by the caller
     * @param operation    metric tag, e.g. {@code jd-analysis}
     * @return the model's JSON content — still unvalidated; the caller must schema-check it
     * @throws GroqException when the call fails after retries, or returns no usable content
     */
    @Override
    public AiChatResult complete(String systemPrompt, String userContent, String operation) {
        Timer.Sample sample = Timer.start(meterRegistry);

        GroqMessages.ChatRequest request = new GroqMessages.ChatRequest(
                properties.model(),
                List.of(GroqMessages.Message.system(systemPrompt),
                        GroqMessages.Message.user(userContent)),
                properties.temperature(),
                properties.maxOutputTokens(),
                GroqMessages.ResponseFormat.jsonObject(),
                // Suppress gpt-oss's reasoning trace in the response — see ChatRequest's own
                // comment on include_reasoning. A no-op for a non-reasoning model.
                Boolean.FALSE);

        try {
            GroqMessages.ChatResponse response = webClient.post()
                    .uri(CHAT_COMPLETIONS)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 429 || status.is5xxServerError(),
                            r -> Mono.error(new GroqException(
                                    "Groq returned " + r.statusCode().value(), true,
                                    r.statusCode().value() == 429, null)))
                    .onStatus(status -> status.is4xxClientError(),
                            r -> Mono.error(new GroqException(
                                    "Groq rejected the request: " + r.statusCode().value(), false)))
                    .bodyToMono(GroqMessages.ChatResponse.class)
                    .retryWhen(Retry.backoff(properties.maxRetries(), Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(20))
                            .jitter(0.4)
                            .filter(this::isRetryable)
                            // Preserve *why* the retries were exhausted. Reporting a 429 as
                            // "Groq unavailable" sent everyone hunting for an outage when the
                            // real cause was this account's per-minute token budget — and no
                            // amount of second-scale backoff can outlast a per-minute window.
                            .onRetryExhaustedThrow((spec, signal) -> {
                                boolean rateLimited = signal.failure() instanceof GroqException groq
                                        && groq.isRateLimited();
                                String reason = rateLimited
                                        ? "Groq rate limit (429) still exceeded after " + signal.totalRetries()
                                                + " retries — the account's per-minute request/token budget is "
                                                + "exhausted, not a Groq outage"
                                        : "Groq unavailable after " + signal.totalRetries() + " retries";
                                return new GroqException(reason, true, rateLimited, signal.failure());
                            }))
                    .block(Duration.ofSeconds(properties.timeoutSeconds() + 5L));

            if (response == null || response.firstContent() == null) {
                throw new GroqException("Groq returned no content", true);
            }
            if ("length".equals(response.firstFinishReason())) {
                // Truncated output is invalid JSON; failing loudly beats persisting a
                // half-written result.
                throw new GroqException("Groq response was truncated at the token limit", false);
            }

            recordUsage(operation, response);
            return new AiChatResult(response.firstContent(), response.model(),
                    response.usage() == null ? 0 : response.usage().total_tokens());

        } catch (GroqException ex) {
            meterRegistry.counter("careerforge.ai.failures", "operation", operation,
                    "retryable", String.valueOf(ex.isRetryable())).increment();
            log.warn("Groq call failed operation={} retryable={} reason={}",
                    operation, ex.isRetryable(), ex.getMessage());
            throw ex;
        } finally {
            sample.stop(meterRegistry.timer("careerforge.ai.latency", "operation", operation));
        }
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof GroqException groq) {
            return groq.isRetryable();
        }
        if (throwable instanceof WebClientResponseException web) {
            return web.getStatusCode().value() == 429 || web.getStatusCode().is5xxServerError();
        }
        // Connection resets and timeouts are worth one more attempt.
        return true;
    }

    private void recordUsage(String operation, GroqMessages.ChatResponse response) {
        if (response.usage() == null) {
            return;
        }
        meterRegistry.counter("careerforge.ai.tokens", "operation", operation, "kind", "prompt")
                .increment(response.usage().prompt_tokens());
        meterRegistry.counter("careerforge.ai.tokens", "operation", operation, "kind", "completion")
                .increment(response.usage().completion_tokens());
        log.info("Groq call ok operation={} model={} promptTokens={} completionTokens={}",
                operation, response.model(),
                response.usage().prompt_tokens(), response.usage().completion_tokens());
    }
}
