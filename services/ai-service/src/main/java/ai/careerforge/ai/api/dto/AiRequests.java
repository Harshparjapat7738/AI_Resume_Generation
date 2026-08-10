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

    /**
     * @param jobTitle            the confirmed target role
     * @param company             the confirmed target company, when known — nullable, since
     *                            not every job description names one; both this and jobTitle
     *                            are allowed to appear in the generated text without an
     *                            evidence citation (see {@code GroundingValidator#validate}
     *                            3-arg overload); everything else still requires one
     * @param selectedEvidenceIds ids chosen in the evidence-selection stage
     * @param evidence            the full inventory, so grounding can resolve every citation
     */
    public record CoverLetterContentRequest(
            @NotBlank @Size(max = 200) String jobTitle,
            @Size(max = 200) String company,
            @Size(max = 100) String seniority,
            @NotEmpty @Valid List<RequirementInput> requirements,
            @NotEmpty List<String> selectedEvidenceIds,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }

    /**
     * @param jobTitle the confirmed target role — allowed as context without an evidence
     *                 citation, same as {@link CoverLetterContentRequest} (see
     *                 {@code GroundingValidator#validate} 3-arg overload)
     * @param company  the confirmed target company, when known — same caveat; nullable,
     *                 since not every job description yields a company name
     * @param evidence the candidate's complete evidence inventory. Email generation is
     *                 single-shot — there is no separate evidence-selection stage; the model
     *                 picks directly from the full inventory, appropriate for one short
     *                 paragraph rather than a full resume or letter
     */
    public record EmailContentRequest(
            @NotBlank @Size(max = 200) String jobTitle,
            @Size(max = 200) String company,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }
}
