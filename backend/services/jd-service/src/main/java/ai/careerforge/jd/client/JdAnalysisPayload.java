package ai.careerforge.jd.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Shape of ai-service's {@code analysis} node — see docs/API_CATALOG.md &sect;4. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JdAnalysisPayload(
        boolean isJobPosting,
        String notReason,
        String jobTitle,
        String company,
        String seniority,
        List<RequirementPayload> requirements,
        List<String> keywords) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementPayload(
            String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }
}
