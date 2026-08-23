package ai.careerforge.ai.client;

/**
 * Provider-agnostic contract for one grounded chat-completion call: a system prompt, untrusted
 * user content already fenced/sanitised by the caller, and an operation tag for metrics/logs.
 *
 * <p><strong>Why this exists:</strong> {@link GroqClient} used to be depended on directly, by
 * concrete type, from every AI generation call site (see docs/current-generation-workflow.md
 * &sect;16 for the pre-abstraction inventory). This interface changes nothing about what Groq
 * does, how it's configured, or how its failures are reported — {@link GroqClient} is, today,
 * still the only implementation, wired automatically by Spring since it is the sole
 * {@code @Component} of this type. It only gives call sites a provider-neutral type to depend
 * on, so a second implementation (a future Gemini client) can be added later without touching
 * any of them again.
 *
 * <p><strong>Deliberately narrow.</strong> This is exactly the shape {@link GroqClient#complete}
 * already had — system prompt in, sanitised/fenced user content in, one JSON-object completion
 * out — and nothing more:
 * <ul>
 *   <li><strong>Structured output/schema</strong> is validated by each caller's own
 *       {@code AiGenerationSupport.validateSchema(...)} <em>after</em> this call returns, never
 *       inside it — so the interface has no schema parameter, matching today's actual call
 *       sites exactly.</li>
 *   <li><strong>Model, temperature and timeout</strong> are per-implementation configuration
 *       (see {@link GroqProperties} / {@link GroqClientConfig} for the Groq values) — no call
 *       site varies them. <strong>The completion-token reservation is the one exception</strong>:
 *       Groq admits a call's {@code max_completion_tokens} against the account's per-minute
 *       token budget at admission time, before generation even starts (see
 *       {@code docs/ARCHITECTURE_DECISIONS.md} ADR-038), so a call whose schema genuinely needs
 *       little output should not reserve as much as one that needs a lot. {@link #complete(String,
 *       String, String)} keeps the old, config-default behaviour unchanged for callers that have
 *       never needed otherwise; {@link #complete(String, String, String, Integer)} lets a caller
 *       state its own, tighter ceiling.</li>
 *   <li><strong>Errors</strong> propagate as an unchecked, implementation-specific exception
 *       (Groq's is {@link GroqException}) exactly as they do today — nothing here mandates a
 *       shared exception type, since no existing call site catches one; only
 *       {@code AiController}'s own diagnostic status check imports {@link GroqException}
 *       directly, and it is intentionally left untouched by this abstraction (see that
 *       controller's Javadoc).</li>
 * </ul>
 *
 * @see GroqClient the sole implementation today
 */
public interface AiChatClient {

    /**
     * Runs one chat completion and returns the model's raw JSON content — still unvalidated;
     * the caller must schema-check it, exactly as before this interface existed.
     *
     * @param systemPrompt the versioned instruction block; never user-supplied
     * @param userContent  the data block, containing untrusted content already delimited and
     *                     labelled by the caller
     * @param operation    metric/log tag, e.g. {@code jd-analysis}
     * @return the model's JSON content plus provenance
     * @throws RuntimeException an implementation-specific unchecked failure (Groq's is
     *         {@link GroqException}) when the call fails after retries, or returns no usable
     *         content
     */
    AiChatResult complete(String systemPrompt, String userContent, String operation);

    /**
     * Same as {@link #complete(String, String, String)}, but reserves at most
     * {@code maxCompletionTokensOverride} completion tokens instead of the implementation's
     * configured default (ADR-038) — the lever that keeps a schema-constrained, low-output
     * operation (JD analysis, adjudication) from reserving far more of the per-minute token
     * budget than it could ever use.
     *
     * @param maxCompletionTokensOverride a tighter ceiling than the configured default; {@code
     *        null} falls back to {@link #complete(String, String, String)}'s behaviour exactly
     */
    default AiChatResult complete(String systemPrompt, String userContent, String operation,
                                  Integer maxCompletionTokensOverride) {
        return complete(systemPrompt, userContent, operation);
    }

    /**
     * One completion's result: the model's raw JSON content, which model actually served the
     * request, and total token usage — the exact fields every call site already persists as
     * provenance. Field-for-field identical to {@code GroqClient}'s former (now removed)
     * {@code GroqResult} record; only the provider-specific name was dropped.
     */
    record AiChatResult(String content, String model, int totalTokens) {
    }
}
