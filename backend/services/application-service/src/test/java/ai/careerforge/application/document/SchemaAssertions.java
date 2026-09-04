package ai.careerforge.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

/** Test-only helper: validates a serialized document model against its {@code schemas/*.json}
 *  file, the same networknt library and draft version ai-service's own {@code SchemaValidator}
 *  uses against model output. */
final class SchemaAssertions {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private SchemaAssertions() {
    }

    static void assertValidAgainstSchema(ObjectMapper mapper, Object model, String schemaFileName) {
        JsonNode node = mapper.valueToTree(model);
        Set<ValidationMessage> errors = schema(schemaFileName).validate(node);
        assertThat(errors).as("schema violations for %s against %s", model.getClass().getSimpleName(),
                schemaFileName).isEmpty();
    }

    private static JsonSchema schema(String schemaFileName) {
        try (InputStream in = new ClassPathResource("schemas/" + schemaFileName).getInputStream()) {
            return FACTORY.getSchema(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing JSON schema: schemas/" + schemaFileName, ex);
        }
    }
}
