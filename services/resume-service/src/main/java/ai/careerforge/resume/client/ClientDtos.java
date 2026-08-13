package ai.careerforge.resume.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Local mirrors of the DTOs owned by profile-service, jd-service and ai-service. Each
 * service defines its own client-side copy of the contracts it consumes — there is no
 * shared DTO module (ADR-006), only an agreed JSON shape.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ---- profile-service --------------------------------------------------

    public record EvidenceItem(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate) {
    }

    // ---- jd-service ---------------------------------------------------------

    public record Requirement(String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    public record JdAnalysis(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<Requirement> requirements) {
    }

    // ---- ai-service -----------------------------------------------------

    public record RequirementInput(String requirementId, String text, String type, int weight) {
    }

    public record EvidenceSelectionRequest(
            List<RequirementInput> requirements, List<EvidenceItem> evidence, Integer promptVersion) {
    }

    public record EvidenceSelectionResponse(JsonNode selection, JsonNode provenance) {
    }

    public record ResumeContentRequest(
            String jobTitle, String seniority, List<RequirementInput> requirements,
            List<String> selectedEvidenceIds, List<EvidenceItem> evidence, Integer promptVersion) {
    }

    public record ResumeContentResponse(JsonNode content, JsonNode grounding, List<String> removedSections, JsonNode provenance) {
    }

    // ---- document-service (custom templates) -------------------------------
    // Mirrors document-service's CustomTemplateAssetResponses exactly, field for field (ADR-023
    // added the PDF-only ones below) — Jackson's default FAIL_ON_UNKNOWN_PROPERTIES means any
    // field present in that response but missing here would break every custom-template upload,
    // not just PDF ones, so this must stay in lockstep with the real response shape.

    public record TemplateStructureDto(
            String sourceType,
            Integer pageWidthTwips, Integer pageHeightTwips, Integer marginTopTwips, Integer marginBottomTwips,
            Integer marginLeftTwips, Integer marginRightTwips, int columnCount, int paragraphCount, int tableCount,
            boolean hasHeader, boolean hasFooter, Integer pageCount, Float pageWidthPt, Float pageHeightPt,
            List<String> fontsUsed, List<Double> fontSizesPt, List<String> colorsUsedHex,
            List<String> alignmentsUsed, List<String> headingsFound) {
    }

    public record DetectedFieldDto(
            String token, String context, String suggestedField,
            Integer pdfPage, Float pdfX, Float pdfY, Float pdfWidth, Float pdfHeight,
            Float pdfFontSizePt, String pdfFontName, String pdfColorHex) {
    }

    public record CustomTemplateAssetDto(
            String id, String originalFilename, String format, long byteSize, String sha256,
            TemplateStructureDto structure, List<DetectedFieldDto> detectedFields, String createdAt) {
    }
}
