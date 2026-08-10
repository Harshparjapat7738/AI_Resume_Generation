package ai.careerforge.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Wire types for Groq's OpenAI-compatible chat-completions API.
 *
 * <p>Grouped in one file because they are a single protocol, meaningless apart, and never
 * used outside {@link GroqClient}.
 */
public final class GroqMessages {

    private GroqMessages() {
    }

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    /** Forces a JSON object response so free-form prose cannot survive parsing. */
    public record ResponseFormat(String type) {

        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            String model,
            List<Message> messages,
            Double temperature,
            Integer max_tokens,
            ResponseFormat response_format) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices, Usage usage, String model) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(Message message, String finish_reason) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {
        }

        public String firstContent() {
            if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
                return null;
            }
            return choices.get(0).message().content();
        }

        public String firstFinishReason() {
            return choices == null || choices.isEmpty() ? null : choices.get(0).finish_reason();
        }
    }
}
