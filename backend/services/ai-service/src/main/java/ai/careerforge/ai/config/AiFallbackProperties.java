package ai.careerforge.ai.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which operations {@link ai.careerforge.ai.client.AiProviderRouter} is allowed to fall back to
 * Gemini for (ADR-039) — the single, centralised place that decision is made, so no business
 * service ever needs to know a fallback provider exists at all.
 *
 * <p>Deliberately an explicit allow-list, not "fall back for everything": ADR-039 enables it for
 * {@code jd-analysis} and {@code jd-optimization} only. Email content and evidence selection are
 * unaffected — their operation tags simply aren't in this set, so a Groq failure there behaves
 * exactly as it always has.
 *
 * @param operations the operation tags (matching each service's {@code PROMPT} constant, e.g.
 *                    {@code jd-analysis}) eligible for a Gemini fallback attempt
 */
@ConfigurationProperties(prefix = "careerforge.ai.fallback")
public record AiFallbackProperties(Set<String> operations) {

    public AiFallbackProperties {
        operations = operations == null ? Set.of() : Set.copyOf(operations);
    }

    public boolean isEnabledFor(String operation) {
        return operations.contains(operation);
    }
}
