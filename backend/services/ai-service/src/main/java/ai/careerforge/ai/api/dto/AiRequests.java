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
     * The JD-optimization stage (ADR-033) — the operation that replaced resume and cover-letter
     * content generation. Takes the same two inputs the removed pipeline took (a confirmed
     * posting's requirements and the candidate's full evidence inventory), plus the keywords the
     * JD analysis already extracted, and returns targeting data rather than prose.
     *
     * @param requirements requirements from a CONFIRMED job description
     * @param keywords     vocabulary already extracted by the JD analysis; optional
     * @param evidence     the candidate's complete evidence inventory — the only source of
     *                     candidate facts this operation has
     */
    public record JdOptimizationRequest(
            @Size(max = 200) String jobTitle,
            @Size(max = 200) String company,
            @Size(max = 60) String seniority,
            @NotEmpty @Valid List<RequirementInput> requirements,
            List<@Size(max = 100) String> keywords,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }

    /**
     * @param jobTitle the confirmed target role — allowed as context without an evidence
     *                 citation (see {@code GroundingValidator#validate}'s 3-arg overload)
     * @param company  the confirmed target company, when known — same caveat; nullable,
     *                 since not every job description yields a company name
     * @param evidence the candidate's complete evidence inventory. Email generation is
     *                 single-shot — there is no separate evidence-selection stage; the model
     *                 picks directly from the full inventory, appropriate for one short
     *                 paragraph
     */
    public record EmailContentRequest(
            @NotBlank @Size(max = 200) String jobTitle,
            @Size(max = 200) String company,
            @NotEmpty @Valid List<EvidenceItem> evidence,
            Integer promptVersion) {
    }
}
