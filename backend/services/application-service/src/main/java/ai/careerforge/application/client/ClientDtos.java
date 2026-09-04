package ai.careerforge.application.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

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

    /** Mirrors jd-service's {@code JdOptimizationResponse} (ADR-033) — {@code optimisation} is
     *  republished verbatim, opaque JSON application-service does not interpret beyond reading
     *  its own {@code emphasis} ranking (see {@code ResumeRenderService}); {@code
     *  citedEvidenceIds} is the provenance trail proving every candidate-facing value traces
     *  back to the caller's own profile. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdOptimizationDto(
            String id, String jobDescriptionId, Map<String, Object> optimisation,
            List<String> citedEvidenceIds, java.time.Instant createdAt) {
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

    /** The candidate's own stated identity fields application-service reads from
     *  {@code GET /api/profile} — {@code fullName} for the email sign-off (never invented,
     *  ADR-019), the rest for the resume-render document header (ADR-036). Never invented,
     *  never evidence-backed — see {@code render-service}'s own {@code DocumentHeader} Javadoc
     *  on why identity data carries no {@code evidenceId}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalInformationDto(String fullName, String email, String phone, List<String> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileDto(PersonalInformationDto personalInformation) {
    }

    /** Mirrors ai-service's {@code EvidenceItem} exactly, so it can be forwarded to
     *  {@code AiServiceClient} unchanged — see {@code GET /api/profile/evidence}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /** {@code bullets} mirrors profile-service's own addition to {@code EvidenceItemResponse}:
     *  the real, unflattened list backing {@code description} for evidence types that have one
     *  (only EXPERIENCE today) — {@code description} itself is unchanged, still the single
     *  flattened blob other consumers of this same client already read. */
    public record EvidenceItem(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate,
            List<String> bullets) {
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

    // ---- render-service (ADR-036) -------------------------------------------------

    /**
     * application-service's own client-side copy of render-service's {@code ResumeRenderRequest}
     * contract (ADR-006: no shared DTO module — the field shapes agree by convention, not by
     * import). {@code template}/{@code outputFormat} are plain strings rather than a shared enum
     * for the same reason: {@code "STANDARD"}/{@code "PDF"} is the entire allowlist either side
     * needs to agree on today, and a plain string round-trips identically to how Jackson already
     * serialises an enum constant by default.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeRenderRequest(
            String schemaVersion, String template, String outputFormat, RenderDocumentHeader header,
            RenderContentLeaf summary, List<RenderResumeSection> sections, RenderHints renderHints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderDocumentHeader(
            String fullName, String email, String phone, String location, List<RenderHeaderLink> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderHeaderLink(String label, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderResumeSection(String heading, List<RenderSectionEntry> entries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderSectionEntry(
            String evidenceId, String title, String organisation, String location,
            String startDate, String endDate, List<RenderContentLeaf> bullets) {
    }

    /** {@code origin} mirrors render-service's {@code ContentOrigin} enum as a plain string —
     *  this integration only ever sends {@code "VERBATIM_FROM_PROFILE"} (see
     *  {@code ResumeRenderService}'s own Javadoc: no AI call, no rephrasing). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderContentLeaf(String text, List<String> evidenceIds, String origin) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderHints(String pageSize, int maxPages, String fontFamily, String accentColorHex) {
    }

    /** Only the fields this integration reads from render-service's {@code RenderResponse} —
     *  {@code document}'s own metadata (page count, size, etc.) is render-service's concern,
     *  not application-service's; {@code @JsonIgnoreProperties} lets it ride along unread. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderResponse(String status, byte[] pdfBytes, List<RenderError> errors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderError(String code, String message) {
    }
}
