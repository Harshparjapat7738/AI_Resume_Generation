package ai.careerforge.application.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Local mirrors of the DTOs owned by jd-service and assessment-service. Each
 * service defines its own client-side copy of the contracts it consumes — there is no shared
 * DTO module (ADR-006), only an agreed JSON shape.
 *
 * <p>All mirrors are {@code @JsonIgnoreProperties(ignoreUnknown = true)}: the owning service
 * is free to add fields without this client needing to change in lockstep or breaking
 * deserialization the moment it doesn't.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ---- jd-service -----------------------------------------------------------

    /** Only the fields application-service actually reads from {@code GET /api/jd/{id}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobDescriptionDto(String id, String status, String title, String company) {
    }

    /** Mirrors jd-service's own {@code Requirement}/{@code JdAnalysis} DTOs —
     *  used to drive cover-letter evidence selection the same way resume generation does. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementDto(
            String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdAnalysisDto(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<RequirementDto> requirements) {
    }

    // ---- resume-service ---------------------------------------------------------

    /** Only the fields application-service actually reads from {@code GET /api/resumes/{id}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeVersionDto(String id, String jobDescriptionId, String jobTitle, String company) {
    }

    /** Only the fields application-service actually reads from
     *  {@code GET /api/resumes/templates/{id}} (ARCHITECTURE_DECISIONS.md ADR-016). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateDto(String templateId) {
    }

    // ---- assessment-service -------------------------------------------------------

    /** Existence is all application-service needs — see {@link ai.careerforge.application.domain.Application}
     *  Javadoc on why there is no separate assessment ID to store. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssessmentDto(String resumeVersionId) {
    }

    // ---- profile-service --------------------------------------------------------

    /** Only the field application-service reads from {@code GET /api/profile} — the
     *  candidate's own stated name for the email sign-off (never invented, ADR-019). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalInformationDto(String fullName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileDto(PersonalInformationDto personalInformation) {
    }

    /** Mirrors ai-service's {@code EvidenceItem} exactly, so it can be forwarded to
     *  {@code AiServiceClient} unchanged — see {@code GET /api/profile/evidence}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceItem(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate) {
    }

    // ---- ai-service -----------------------------------------------------------

    public record EmailContentRequest(
            String jobTitle, String company, List<EvidenceItem> evidence, Integer promptVersion) {
    }

    public record EmailContentResponse(JsonNode content, JsonNode grounding, List<String> removedParagraphs,
                                       JsonNode provenance) {
    }

    /** Mirrors resume-service's own client-side copies of ai-service's evidence-selection
     *  contract (ADR-006 — no shared DTO module). */
    public record RequirementInput(String requirementId, String text, String type, int weight) {
    }

    public record EvidenceSelectionRequest(
            List<RequirementInput> requirements, List<EvidenceItem> evidence, Integer promptVersion) {
    }

    public record EvidenceSelectionResponse(JsonNode selection, JsonNode provenance) {
    }

    public record CoverLetterContentRequest(
            String jobTitle, String company, String seniority, List<RequirementInput> requirements,
            List<String> selectedEvidenceIds, List<EvidenceItem> evidence, Integer promptVersion) {
    }

    public record CoverLetterContentResponse(JsonNode content, JsonNode grounding,
                                             List<String> removedParagraphs, JsonNode provenance) {
    }
}
