package ai.careerforge.ai.client;

/**
 * Provider-agnostic contract for one grounded chat-completion call: a system prompt, untrusted
 * user content already fenced/sanitised by the caller, and an operation tag for metrics/logs.
 *
 * <p><strong>Why this exists:</strong> {@link GroqClient} used to be depended on directly, by
 * concrete type, from every AI generation call site. This interface changes nothing about what
 * either provider does, how it's configured, or how its failures are reported — it only gives
 * call sites a provider-neutral type to depend on. <strong>Since ADR-039, the sole
 * {@code @Component} of this type is {@link AiProviderRouter}</strong>, not {@link GroqClient}
 * directly: the router tries Groq first and falls back to {@link GeminiClient} for the handful
 * of operations configured for it (JD analysis, JD-optimization adjudication), when Groq's
 * failure is one Gemini could plausibly succeed at — never for every operation, never when
 * Groq succeeds, never for a deterministic application-side bug. Every business service
 * (`JdAnalysisService`, `JdOptimizationService`, `EvidenceSelectionService`,
 * `EmailContentService`) still injects this interface exactly as before ADR-039 and contains no
 * provider-specific branching whatsoever — that decision lives in exactly one place, the router.
 *
 * <p><strong>Deliberately narrow.</strong> This is exactly the shape {@link GroqClient#complete}
 * already had — system prompt in, sanitised/fenced user content in, one JSON-object completion
 * out — and nothing more:
 * <ul>
 *   <li><strong>Structured output/schema</strong> is validated by each caller's own
 *       {@code AiGenerationSupport.validateSchema(...)} <em>after</em> this call returns, never
 *       inside it — so the interface has no schema parameter, matching today's actual call
 *       sites exactly. (Gemini additionally receives a provider-specific structured-output
 *       schema hint at request time, entirely inside {@link GeminiClient} — the response is
 *       still validated against the exact same canonical schema file Groq's output is.)</li>
 *   <li><strong>Model, temperature and timeout</strong> are per-implementation configuration
 *       (see {@link GroqProperties} / {@link GroqClientConfig} for the Groq values, their
 *       Gemini counterparts for the fallback) — no call site varies them.
 *       <strong>The completion-token reservation is the one exception</strong>: both providers
 *       admit a call's requested output budget against a per-minute quota at admission time,
 *       before generation even starts (ADR-038), so a call whose schema genuinely needs little
 *       output should not reserve as much as one that needs a lot. {@link #complete(String,
 *       String, String)} keeps the old, config-default behaviour unchanged for callers that have
 *       never needed otherwise; {@link #complete(String, String, String, Integer)} lets a caller
 *       state its own, tighter ceiling — honoured by whichever provider ends up serving the
 *       call.</li>
 *   <li><strong>Errors</strong> propagate as {@link AiProviderException} (ADR-039) — a single,
 *       normalised, provider-tagged exception type, regardless of which provider(s) were tried.
 *       Callers never catch a provider-specific exception ({@link GroqException}/
 *       {@link GeminiException}); only {@link AiProviderRouter} itself does, to decide whether a
 *       Groq failure is worth a Gemini attempt.</li>
 * </ul>
 *
 * @see AiProviderRouter the sole implementation businesses inject
 * @see GroqClient the primary provider
 * @see GeminiClient the fallback provider
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
     * @throws AiProviderException when every attempted provider failed
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
     * request, which provider that model belongs to, and total token usage — the exact fields
     * every call site already persists as provenance.
     *
     * @param provider which provider actually served this request (ADR-039) — telemetry only;
     *                 business services never branch on it
     */
    record AiChatResult(String content, String model, int totalTokens, AiProvider provider) {
    }
}
