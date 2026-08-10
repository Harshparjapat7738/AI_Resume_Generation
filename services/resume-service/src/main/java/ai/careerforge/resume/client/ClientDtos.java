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
}
