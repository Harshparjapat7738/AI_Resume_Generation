package ai.careerforge.ai.schema;

import java.util.List;

/** Model output did not match its contract. */
public class SchemaValidationException extends RuntimeException {

    private final String schemaName;
    private final List<String> violations;

    public SchemaValidationException(String schemaName, List<String> violations) {
        super("Model output violated " + schemaName);
        this.schemaName = schemaName;
        this.violations = List.copyOf(violations);
    }

    public String schemaName() {
        return schemaName;
    }

    /** Safe to log and to surface internally; contains schema paths, not user content. */
    public List<String> violations() {
        return violations;
    }
}
