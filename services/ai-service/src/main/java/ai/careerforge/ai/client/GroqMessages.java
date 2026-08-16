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

    /** {@code @JsonIgnoreProperties} because this doubles as the response-side shape too
     *  (see {@code Choice.message}): reasoning-capable models (e.g. {@code openai/gpt-oss-120b})
     *  add extra fields here (a {@code reasoning} field, when not suppressed by
     *  {@code include_reasoning=false} below) that this client has no use for and must not choke
     *  on — the same defensive stance {@link ChatResponse}/{@link Choice}/{@link Usage} already
     *  take. */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
            // Groq deprecated `max_tokens` in favor of `max_completion_tokens` (identical
            // semantics) across every model on the platform, not just reasoning ones — see
            // console.groq.com/docs/api-reference.
            Integer max_completion_tokens,
            ResponseFormat response_format,
            // openai/gpt-oss-120b (and -20b) include a chain-of-thought `reasoning` field on the
            // assistant message by default, which this client never reads and which would only
            // burn response-buffer/token budget for no benefit here — suppressed the same way
            // the request never asks for anything else this app doesn't use. `null` (omitted,
            // see @JsonInclude above) for any future non-reasoning model, where the field isn't
            // meaningful.
            Boolean include_reasoning) {
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
