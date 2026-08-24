package ai.careerforge.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gemini configuration (ADR-039) — the fallback provider for the two operations configured in
 * {@code careerforge.ai.fallback.operations} (JD analysis, JD-optimization adjudication).
 *
 * <p>Unlike {@link GroqProperties}, this is <strong>optional</strong>: Gemini is not required
 * for the platform to start or to serve any request. {@link #enabled()} reflects both the
 * {@code enabled} flag and whether an API key is actually present, so a deployment with no
 * {@code GEMINI_API_KEY} simply never attempts a fallback — Groq-only behaviour, unchanged.
 *
 * @param enabled         operator kill-switch, independent of whether a key happens to be set
 * @param apiKey          {@code GEMINI_API_KEY} — secret, never logged, never returned to a client
 * @param baseUrl         Gemini's REST base URL
 * @param model           model identifier, e.g. {@code gemini-3.6-flash} — must support
 *                        structured JSON output
 * @param timeoutSeconds  per-request timeout
 * @param maxOutputTokens upper bound on generated tokens, mirroring {@code GroqProperties}'
 *                        role — a caller's own tighter per-call override still wins (ADR-038)
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.gemini")
public record GeminiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        @Min(5) @Max(300) int timeoutSeconds,
        @Min(256) @Max(32768) int maxOutputTokens) {

    /** True only when both the operator flag is on and a key is actually configured — the one
     *  condition {@link ai.careerforge.ai.client.AiProviderRouter} checks before ever
     *  considering a Gemini fallback. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** Redacted view for logs and diagnostics — never exposes the key itself. */
    public String maskedKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }
}
