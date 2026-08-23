package ai.careerforge.ai.service;

import ai.careerforge.ai.client.AiChatClient;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.schema.SchemaValidationException;
import ai.careerforge.ai.schema.SchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared plumbing for the generation stages: prompt resolution, schema validation, and
 * translation of internal failures into the platform error envelope.
 */
@Component
public class AiGenerationSupport {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationSupport.class);
    /** ADR-038: a JSON-repair retry is cheap by design — it only asks the model to re-emit the
     *  same content, syntactically fixed, never to reconsider it. */
    private static final int REPAIR_MAX_COMPLETION_TOKENS = 300;

    private final PromptRegistry promptRegistry;
    private final SchemaValidator schemaValidator;

    public AiGenerationSupport(PromptRegistry promptRegistry, SchemaValidator schemaValidator) {
        this.promptRegistry = promptRegistry;
        this.schemaValidator = schemaValidator;
    }

    /** Resolves a pinned version when supplied, otherwise the newest. */
    public PromptRegistry.Prompt resolvePrompt(String name, Integer version) {
        try {
            return version == null ? promptRegistry.latest(name)
                    : promptRegistry.get(name, version);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unknown prompt version requested.");
        }
    }

    /**
     * Validates model output against its schema.
     *
     * <p>A schema failure is reported as {@code AI_GENERATION_FAILED} rather than
     * {@code INTERNAL_ERROR}: it is a model problem, is usually transient, and the caller
     * can reasonably retry.
     */
    public JsonNode validateSchema(String rawJson, String schemaName, String operation) {
        try {
            return schemaValidator.validate(rawJson, schemaName);
        } catch (SchemaValidationException ex) {
            log.warn("Schema validation failed operation={} schema={} violations={}",
                    operation, ex.schemaName(), ex.violations());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED,
                    ErrorCode.AI_GENERATION_FAILED.defaultMessage());
        }
    }

    /**
     * Runs one completion, reserving at most {@code maxCompletionTokens} (ADR-038 — far below
     * the old blanket 4,096 default for a schema-constrained operation), validates it against
     * {@code schemaName}, and — <strong>only</strong> when the failure was genuinely malformed
     * JSON, never a schema violation — makes exactly one cheap repair attempt: the parse error
     * and the malformed content are sent back, capped at {@value #REPAIR_MAX_COMPLETION_TOKENS}
     * completion tokens, asking only for the same content re-emitted with valid JSON syntax.
     *
     * <p>A schema violation (valid JSON that simply doesn't match the contract) is never
     * repaired here — that is a prompt or schema defect, not a one-off glitch, and retrying it
     * unchanged would just reproduce it (ADR-038's guidance on {@code finish_reason=length}
     * applies by the same logic).
     */
    public ValidatedCompletion completeAndValidate(AiChatClient aiChatClient, String systemPrompt,
            String userContent, String operation, String schemaName, int maxCompletionTokens) {
        AiChatClient.AiChatResult first =
                aiChatClient.complete(systemPrompt, userContent, operation, maxCompletionTokens);
        try {
            return new ValidatedCompletion(schemaValidator.validate(first.content(), schemaName), first, false);
        } catch (SchemaValidationException firstFailure) {
            if (!firstFailure.isMalformedJson()) {
                log.warn("Schema validation failed operation={} schema={} violations={}",
                        operation, schemaName, firstFailure.violations());
                throw new ApiException(ErrorCode.AI_GENERATION_FAILED,
                        ErrorCode.AI_GENERATION_FAILED.defaultMessage());
            }

            log.warn("Model output was not valid JSON operation={} schema={} — attempting one repair",
                    operation, schemaName);
            String repairPrompt = userContent + "\n\n-----CORRECTION-----\n"
                    + "Your previous response was not valid JSON and could not be parsed. "
                    + "Re-emit the SAME content, corrected to be syntactically valid JSON matching "
                    + "the required schema. Return only the JSON object, nothing else.\n"
                    + "-----CORRECTION-----";
            AiChatClient.AiChatResult repaired = aiChatClient.complete(
                    systemPrompt, repairPrompt, operation, REPAIR_MAX_COMPLETION_TOKENS);
            try {
                return new ValidatedCompletion(schemaValidator.validate(repaired.content(), schemaName), repaired, true);
            } catch (SchemaValidationException secondFailure) {
                log.warn("Repair attempt also failed operation={} schema={} violations={}",
                        operation, schemaName, secondFailure.violations());
                throw new ApiException(ErrorCode.AI_GENERATION_FAILED,
                        ErrorCode.AI_GENERATION_FAILED.defaultMessage());
            }
        }
    }

    /** @param repaired true when the first attempt was malformed JSON and this is the one
     *                  permitted repair's result */
    public record ValidatedCompletion(JsonNode content, AiChatClient.AiChatResult result, boolean repaired) {
    }
}
