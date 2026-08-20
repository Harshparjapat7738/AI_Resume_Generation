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

    /** Mirrors {@code GET /api/jd/{id}/optimization} (ADR-033) — the product's primary
     *  deliverable, and the source of truth {@code ResumeRenderService} draws on to decide
     *  which evidence belongs on a resume for this job and what to lead with.
     *  {@code optimisation} is passed through exactly as jd-service already treats it: an
     *  opaque, already-verified map (keywords/requirementMatches/missingRequirements/emphasis)
     *  jd-service itself never interprets structurally beyond persisting and republishing it.
     *  {@code citedEvidenceIds} is the complete, already-stripped-of-hallucinations set of
     *  evidence ids the optimization actually relies on — see
     *  {@code JdOptimizationService.stripUnknownIds}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdOptimizationDto(
            String id, String jobDescriptionId, java.util.Map<String, Object> optimisation,
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

    /** The fields application-service reads from {@code GET /api/profile}: the candidate's own
     *  stated name (email sign-off, ADR-019, and the resume header) plus contact details the
     *  resume header also needs (ADR-036 — no evidenceId, no photo; identity data, not a
     *  candidate claim). The single-arg constructor keeps every existing email-only call site
     *  working unchanged. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalInformationDto(String fullName, String email, String phone, List<String> links) {

        public PersonalInformationDto(String fullName) {
            this(fullName, null, null, List.of());
        }
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

    // ---- render-service ---------------------------------------------------------

    /**
     * Local mirrors of render-service's own request/response contract (ADR-036). Enum-shaped
     * fields ({@code origin}, {@code heading}, {@code template}, {@code outputFormat},
     * {@code pageSize}, {@code fontFamily}, {@code status}) are plain {@code String}s here
     * rather than importing render-service's Java enums — a different Maven module, and the
     * same "no shared DTO module" reasoning (ADR-006) that already applies to every other
     * client mirror in this file. Jackson serialises a Java enum by its constant name by
     * default, so a matching literal String round-trips against render-service's real
     * enum-typed fields without any conversion code.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderContentLeaf(String text, List<String> evidenceIds, String origin) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderHeaderLink(String label, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderDocumentHeader(
            String fullName, String email, String phone, String location, List<RenderHeaderLink> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderSectionEntry(
            String evidenceId, String title, String organisation, String location,
            String startDate, String endDate, List<RenderContentLeaf> bullets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderResumeSection(String heading, List<RenderSectionEntry> entries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderHints(String pageSize, int maxPages, String fontFamily, String accentColorHex) {
    }

    public record ResumeRenderRequest(
            String schemaVersion, String template, String outputFormat, RenderDocumentHeader header,
            RenderContentLeaf summary, List<RenderResumeSection> sections, RenderHints renderHints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderDocumentMetadata(
            String documentId, String format, long sizeBytes, int pageCount, java.time.Instant renderedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderErrorDto(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderResponse(
            String status, RenderDocumentMetadata document, byte[] pdfBytes, List<RenderErrorDto> errors) {
    }
}
