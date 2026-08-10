package ai.careerforge.ai.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request bodies for the internal AI endpoints. */
public final class AiRequests {

    private AiRequests() {
    }

    /**
     * @param jobDescriptionText raw JD text — untrusted, fenced before it reaches the model
     * @param promptVersion      pin a specific prompt version; null uses the latest
     */
    public record JdAnalysisRequest(
            @NotBlank @Size(min = 50, max = 60_000) String jobDescriptionText,
            Integer promptVersion) {
    }

    /**
     * @param requirements requirements from a CONFIRMED job description
     * @param evidence     the candidate's complete evidence inventory
     */
    public record EvidenceSelectionRequest(
            @NotEmpty @Valid List<RequirementInput> requirements,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }

    public record RequirementInput(
            @NotBlank String requirementId,
            @NotBlank @Size(max = 1000) String text,
            @NotBlank String type,
            int weight) {
    }

    /**
     * @param selectedEvidenceIds ids chosen in the evidence-selection stage
     * @param evidence            the full inventory, so grounding can resolve every citation
     */
    public record ResumeContentRequest(
            @NotBlank @Size(max = 200) String jobTitle,
            @Size(max = 100) String seniority,
            @NotEmpty @Valid List<RequirementInput> requirements,
            @NotEmpty List<String> selectedEvidenceIds,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }
}
