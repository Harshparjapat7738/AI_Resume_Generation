package ai.careerforge.assessment.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3, keyed by {@code resumeVersionId} alone rather than
 * {@code resumeVersionId + jdVersionId} — resume-service doesn't track a jdVersionId today
 * (see ADR-014), and a resume version is generated against exactly one JD, so this is
 * equivalent in practice. {@code recommendations} is embedded here rather than a separate
 * collection: it's bounded and always read together with the fit score, which is exactly
 * when docs/DATABASE.md &sect;2 says to embed rather than reference.
 */
@Document(collection = "jd_fit_assessments")
public class JdFitAssessment {

    @Id
    private String id;

    @Field("resumeVersionId")
    private String resumeVersionId;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("compatibilityScore")
    private double compatibilityScore;

    @Field("coverage")
    private double coverage;

    @Field("keywordMatch")
    private double keywordMatch;

    @Field("seniorityMatch")
    private double seniorityMatch;

    @Field("recency")
    private double recency;

    @Field("requirementMatches")
    private List<RequirementMatchResult> requirementMatches;

    @Field("unmetHardRequirements")
    private List<RequirementMatchResult> unmetHardRequirements;

    @Field("matchedKeywords")
    private List<String> matchedKeywords;

    @Field("missingKeywords")
    private List<String> missingKeywords;

    @Field("readinessBand")
    private String readinessBand;

    @Field("bandRule")
    private String bandRule;

    @Field("recommendations")
    private List<RecommendationItem> recommendations;

    @CreatedDate
    @Field("assessedAt")
    private Instant assessedAt;

    protected JdFitAssessment() {
        // Spring Data
    }

    public JdFitAssessment(String resumeVersionId, String userId, String jobDescriptionId,
                           double compatibilityScore, double coverage, double keywordMatch,
                           double seniorityMatch, double recency,
                           List<RequirementMatchResult> requirementMatches,
                           List<RequirementMatchResult> unmetHardRequirements,
                           List<String> matchedKeywords, List<String> missingKeywords,
                           String readinessBand, String bandRule,
                           List<RecommendationItem> recommendations) {
        this.resumeVersionId = resumeVersionId;
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.compatibilityScore = compatibilityScore;
        this.coverage = coverage;
        this.keywordMatch = keywordMatch;
        this.seniorityMatch = seniorityMatch;
        this.recency = recency;
        this.requirementMatches = List.copyOf(requirementMatches);
        this.unmetHardRequirements = List.copyOf(unmetHardRequirements);
        this.matchedKeywords = List.copyOf(matchedKeywords);
        this.missingKeywords = List.copyOf(missingKeywords);
        this.readinessBand = readinessBand;
        this.bandRule = bandRule;
        this.recommendations = List.copyOf(recommendations);
    }

    public String id() {
        return id;
    }

    public String resumeVersionId() {
        return resumeVersionId;
    }

    public double compatibilityScore() {
        return compatibilityScore;
    }

    public double coverage() {
        return coverage;
    }

    public double keywordMatch() {
        return keywordMatch;
    }

    public double seniorityMatch() {
        return seniorityMatch;
    }

    public double recency() {
        return recency;
    }

    public List<RequirementMatchResult> requirementMatches() {
        return requirementMatches;
    }

    public List<RequirementMatchResult> unmetHardRequirements() {
        return unmetHardRequirements;
    }

    public List<String> matchedKeywords() {
        return matchedKeywords;
    }

    public List<String> missingKeywords() {
        return missingKeywords;
    }

    public String readinessBand() {
        return readinessBand;
    }

    public String bandRule() {
        return bandRule;
    }

    public List<RecommendationItem> recommendations() {
        return recommendations;
    }

    public Instant assessedAt() {
        return assessedAt;
    }
}
