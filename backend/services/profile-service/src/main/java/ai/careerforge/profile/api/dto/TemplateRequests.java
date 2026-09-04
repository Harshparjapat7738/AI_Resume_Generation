package ai.careerforge.profile.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TemplateRequests {

    private TemplateRequests() {
    }

    public record RenameTemplateRequest(@NotBlank @Size(max = 120) String name) {
    }
}
