package ai.careerforge.resume.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ResumeResponses {

    private ResumeResponses() {
    }

    public record GapResponse(String requirementId, String text, String type) {
    }

    public record ResumeVersionResponse(
            String id,
            String jobDescriptionId,
            String jobTitle,
            String company,
            String templateId,
            String templateVersion,
            Map<String, Object> content,
            List<Map<String, Object>> evidenceMatches,
            List<GapResponse> gaps,
            Map<String, Object> grounding,
            List<String> removedSections,
            Instant createdAt) {
    }

    /** Lightweight row for the generation-history dashboard — no content payload. */
    public record ResumeSummaryResponse(
            String id, String jobDescriptionId, String jobTitle, String company, String templateId,
            Instant createdAt) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
