package ai.careerforge.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Wire types for Gemini's {@code generateContent} REST API (ADR-039). Grouped in one file for
 * the same reason {@code GroqMessages} is — a single protocol, meaningless apart, never used
 * outside {@link GeminiClient}.
 */
public final class GeminiMessages {

    private GeminiMessages() {
    }

    public record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(String role, List<Part> parts) {

        public static Content user(String text) {
            return new Content("user", List.of(new Part(text)));
        }
    }

    public record SystemInstruction(List<Part> parts) {

        public static SystemInstruction of(String text) {
            return new SystemInstruction(List.of(new Part(text)));
        }
    }

    /** {@code responseSchema} is Gemini's structured-output constraint (ADR-039 §7) — a
     *  restricted OpenAPI-subset mirror of the same canonical JSON schema Groq's output is
     *  validated against, never the arbitrary-JSON prompt-only approach.
     *
     *  <p>{@code thinkingConfig} matters more than it looks: live-verified against the real API
     *  (ADR-039), {@code gemini-3.6-flash} reasons by default and that reasoning is billed
     *  against the same {@code maxOutputTokens} budget as the visible answer — a trivial prompt
     *  with no {@code thinkingConfig} spent ~290 hidden tokens before writing 5 visible ones,
     *  and the model rejects {@code thinkingBudget:0} outright (400 INVALID_ARGUMENT) for this
     *  model. {@code thinkingLevel:"LOW"} is what actually works: verified live to bring that
     *  same call down to effectively zero hidden tokens without changing {@code
     *  maxOutputTokens} at all — exactly ADR-039's "do not raise the budget" constraint, solved
     *  by spending less of it on invisible reasoning instead. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(Double temperature, Integer maxOutputTokens,
                                   String responseMimeType, JsonNode responseSchema,
                                   ThinkingConfig thinkingConfig) {
    }

    public record ThinkingConfig(String thinkingLevel) {

        public static ThinkingConfig low() {
            return new ThinkingConfig("LOW");
        }
    }

    public record GenerateContentRequest(SystemInstruction systemInstruction, List<Content> contents,
                                         GenerationConfig generationConfig) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateContentResponse(List<Candidate> candidates, UsageMetadata usageMetadata,
                                          String modelVersion) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Candidate(Content content, String finishReason) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount,
                                    Integer totalTokenCount) {
        }

        public String firstText() {
            if (candidates == null || candidates.isEmpty() || candidates.get(0).content() == null
                    || candidates.get(0).content().parts() == null
                    || candidates.get(0).content().parts().isEmpty()) {
                return null;
            }
            return candidates.get(0).content().parts().get(0).text();
        }

        public String firstFinishReason() {
            return candidates == null || candidates.isEmpty() ? null : candidates.get(0).finishReason();
        }
    }

    /** Gemini's error envelope — {@code {"error":{"code":429,"message":"...","status":
     *  "RESOURCE_EXHAUSTED"}}}. Only the {@code status} field is used for classification; the
     *  message is never logged (it can echo prompt content back). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorEnvelope(ErrorBody error) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ErrorBody(Integer code, String status) {
        }
    }
}
