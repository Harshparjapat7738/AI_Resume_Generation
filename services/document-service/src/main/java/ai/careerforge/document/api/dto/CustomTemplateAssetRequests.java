package ai.careerforge.document.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class CustomTemplateAssetRequests {

    private CustomTemplateAssetRequests() {
    }

    /** {@code fieldMappings} is caller-supplied at generation time rather than stored here —
     *  the mapping itself lives on resume-service's catalogue row (the thing a user actually
     *  edits); this service stays stateless about it, exactly like it already knows nothing
     *  about a built-in template's display metadata. */
    public record GenerateRequest(@NotBlank String resumeVersionId, Map<String, String> fieldMappings) {
    }
}
