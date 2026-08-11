package ai.careerforge.resume.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class TemplateRequests {

    private TemplateRequests() {
    }

    public record RenameTemplateRequest(@NotBlank @Size(max = 120) String name) {
    }

    /** {@code null}/missing entries are fine — a mapping in progress doesn't need every
     *  detected field resolved yet; {@link ai.careerforge.resume.service.TemplateService}
     *  only ever writes whatever's actually present here. */
    public record UpdateMappingRequest(Map<String, String> mappings) {
    }
}
