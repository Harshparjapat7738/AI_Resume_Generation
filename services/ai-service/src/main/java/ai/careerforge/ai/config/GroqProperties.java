package ai.careerforge.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Groq configuration. Bound from environment variables so the key never appears in a
 * source file, a YAML file or a Docker image.
 *
 * <p>The model is configuration rather than a constant because Groq deprecates and renames
 * models; pinning it here means an upgrade is a config change, not a code change
 * (blueprint §37).
 *
 * @param apiKey          GROQ_API_KEY — secret, never logged, never returned to a client
 * @param baseUrl         OpenAI-compatible base URL
 * @param model           model identifier, e.g. {@code llama-3.3-70b-versatile}
 * @param timeoutSeconds  per-request timeout
 * @param maxOutputTokens upper bound on generated tokens, to cap cost and latency
 * @param temperature     low by default: this is extraction, not creative writing
 * @param maxRetries      retries for 429/5xx only; never for a 4xx we caused
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.groq")
public record GroqProperties(
        String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @Min(5) @Max(300) int timeoutSeconds,
        @Min(256) @Max(32768) int maxOutputTokens,
        double temperature,
        @Min(0) @Max(5) int maxRetries) {

    public GroqProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is not set. ai-service cannot start without it. "
                            + "See docs/EXTERNAL_APIS.md → Groq.");
        }
    }

    /** Redacted view for logs and diagnostics — never exposes the key itself. */
    public String maskedKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }
}
