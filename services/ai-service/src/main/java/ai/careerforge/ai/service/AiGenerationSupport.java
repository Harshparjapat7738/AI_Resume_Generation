package ai.careerforge.ai.service;

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
 * Shared plumbing for the three generation stages: prompt resolution, schema validation
 * with one retry, and translation of internal failures into the platform error envelope.
 */
@Component
public class AiGenerationSupport {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationSupport.class);

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
}
