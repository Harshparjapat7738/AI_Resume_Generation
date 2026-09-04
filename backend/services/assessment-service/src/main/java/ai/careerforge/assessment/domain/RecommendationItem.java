package ai.careerforge.assessment.domain;

/**
 * A gap or suggestion — never an instruction to invent something the candidate doesn't
 * have. See {@code JdFitScoringEngine} for how these are worded.
 */
public class RecommendationItem {

    private String type;
    private String severity;
    private String message;
    private String relatedRequirementId;

    protected RecommendationItem() {
        // Spring Data
    }

    public RecommendationItem(String type, String severity, String message, String relatedRequirementId) {
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.relatedRequirementId = relatedRequirementId;
    }

    public String type() {
        return type;
    }

    public String severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public String relatedRequirementId() {
        return relatedRequirementId;
    }
}
