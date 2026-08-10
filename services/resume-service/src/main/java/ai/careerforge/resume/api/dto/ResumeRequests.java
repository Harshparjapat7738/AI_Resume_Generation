package ai.careerforge.resume.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class ResumeRequests {

    private ResumeRequests() {
    }

    /** {@code templateId} is optional — omitting it selects the default built-in template
     *  (see {@code TemplateService#DEFAULT_TEMPLATE_ID}), which keeps callers that predate
     *  template selection working unchanged. */
    public record GenerateRequest(@NotBlank String jobDescriptionId, String templateId) {
    }
}
