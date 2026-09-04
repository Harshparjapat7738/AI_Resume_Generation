package ai.careerforge.ai.schema;

import java.util.List;

/** Model output did not match its contract. */
public class SchemaValidationException extends RuntimeException {

    private final String schemaName;
    private final List<String> violations;
    private final boolean malformedJson;

    public SchemaValidationException(String schemaName, List<String> violations) {
        this(schemaName, violations, false);
    }

    public SchemaValidationException(String schemaName, List<String> violations, boolean malformedJson) {
        super("Model output violated " + schemaName);
        this.schemaName = schemaName;
        this.violations = List.copyOf(violations);
        this.malformedJson = malformedJson;
    }

    public String schemaName() {
        return schemaName;
    }

    /** Safe to log and to surface internally; contains schema paths, not user content. */
    public List<String> violations() {
        return violations;
    }

    /** True when the content wasn't even parseable JSON (as opposed to valid JSON that violated
     *  the schema) — the one case worth a single, cheap repair attempt rather than a full
     *  regenerate, since the model most likely just needs to re-emit the same content
     *  syntactically fixed. */
    public boolean isMalformedJson() {
        return malformedJson;
    }
}
