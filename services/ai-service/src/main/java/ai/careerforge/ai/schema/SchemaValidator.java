package ai.careerforge.ai.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Validates model output against a JSON Schema before anything else looks at it.
 *
 * <p>This is the first hard gate on model output and it does double duty: it catches
 * malformed generations, and it is the last line of defence against prompt injection —
 * an instruction-following response is prose, prose is not the required object shape, and
 * so it is rejected here rather than reaching a user's resume.
 */
@Component
public class SchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    public SchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses and validates model output.
     *
     * @param rawJson    the model's raw content
     * @param schemaName file name under {@code classpath:schemas/}
     * @return the parsed tree
     * @throws SchemaValidationException if the content is not JSON or violates the schema
     */
    public JsonNode validate(String rawJson, String schemaName) {
        JsonNode node;
        try {
            node = objectMapper.readTree(stripCodeFence(rawJson));
        } catch (IOException ex) {
            // The body may contain JD or profile text — log the reason, never the content.
            log.warn("Model output was not valid JSON schema={} reason={}",
                    schemaName, ex.getOriginalMessage());
            throw new SchemaValidationException(schemaName,
                    List.of("Response was not valid JSON"));
        }

        Set<ValidationMessage> errors = schema(schemaName).validate(node);
        if (!errors.isEmpty()) {
            List<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .limit(20)
                    .toList();
            log.warn("Model output failed schema validation schema={} violations={}",
                    schemaName, messages.size());
            throw new SchemaValidationException(schemaName, messages);
        }
        return node;
    }

    /**
     * Some models wrap JSON in a markdown fence despite being asked not to. Unwrapping is
     * cheaper and more predictable than a regeneration round trip.
     */
    private String stripCodeFence(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return trimmed;
    }

    private JsonSchema schema(String schemaName) {
        return cache.computeIfAbsent(schemaName, name -> {
            try (InputStream in = new ClassPathResource("schemas/" + name).getInputStream()) {
                return factory.getSchema(in);
            } catch (IOException ex) {
                throw new UncheckedIOException("Missing JSON schema: schemas/" + name, ex);
            }
        });
    }
}
