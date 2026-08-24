package ai.careerforge.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link GeminiProperties#isUsable()} is the one gate {@code AiProviderRouter} checks before
 *  ever considering a Gemini fallback (ADR-039) — a deployment with no key must behave exactly
 *  like Groq-only, never half-configured. {@link GeminiProperties#maskedKey()} mirrors
 *  {@code GroqProperties}' own — the key itself must never appear in logs/diagnostics. */
class GeminiPropertiesTest {

    @Test
    void usableOnlyWhenEnabledAndKeyPresent() {
        assertThat(new GeminiProperties(true, "a-real-key", null, "m", 60, 1200).isUsable()).isTrue();
        assertThat(new GeminiProperties(false, "a-real-key", null, "m", 60, 1200).isUsable()).isFalse();
        assertThat(new GeminiProperties(true, "", null, "m", 60, 1200).isUsable()).isFalse();
        assertThat(new GeminiProperties(true, null, null, "m", 60, 1200).isUsable()).isFalse();
        assertThat(new GeminiProperties(true, "   ", null, "m", 60, 1200).isUsable()).isFalse();
    }

    @Test
    void maskedKeyNeverExposesTheRealValue() {
        String masked = new GeminiProperties(true, "AQ.Ab8RN6Ic0PwnGa8WTgJ", null, "m", 60, 1200).maskedKey();

        assertThat(masked).doesNotContain("Ab8RN6Ic0PwnGa8WTgJ");
        assertThat(masked).startsWith("AQ.");
    }

    @Test
    void shortOrMissingKeyMasksToPlaceholder() {
        assertThat(new GeminiProperties(true, null, null, "m", 60, 1200).maskedKey()).isEqualTo("****");
        assertThat(new GeminiProperties(true, "short", null, "m", 60, 1200).maskedKey()).isEqualTo("****");
    }
}
