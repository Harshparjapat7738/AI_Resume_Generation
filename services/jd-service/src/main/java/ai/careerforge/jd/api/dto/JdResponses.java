package ai.careerforge.jd.api.dto;

import java.time.Instant;
import java.util.List;

public final class JdResponses {

    private JdResponses() {
    }

    public record JdSummaryResponse(
            String id, String status, String sourceType, String title, String company, Instant createdAt) {
    }

    public record JdDetailResponse(
            String id, String status, String sourceType, String title, String company,
            String rawText, int currentVersion, Instant createdAt) {
    }

    public record RequirementResponse(
            String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    public record JdAnalysisResponse(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<RequirementResponse> requirements) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
