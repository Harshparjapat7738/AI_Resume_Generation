package ai.careerforge.assessment.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * ATS structural score (ADR-040, reviving what ADR-033 deferred and ADR-036 left deferred).
 * Scored from the same deterministically-assembled resume content {@code ResumeRenderService}
 * builds for {@code render-service} — never a rendered PDF/DOCX — so it exists whether or not
 * that render call later succeeds. Collection is deliberately {@code
 * ats_structural_assessments}, not the legacy, dead {@code ats_assessments} (still
 * {@code resumeVersionId}-keyed, still unread — see docs/DATABASE.md) — keeping the two apart is
 * exactly the confusion CLAUDE.md's "Known loose ends" already warns about for other renamed
 * collections. Keyed on {@code jdOptimizationId}, the same natural key {@link JdFitAssessment}
 * already uses and for the same reason: one current optimization per JD version.
 */
@Document(collection = "ats_structural_assessments")
public class AtsAssessment {

    @Id
    private String id;

    @Field("jdOptimizationId")
    private String jdOptimizationId;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("atsScore")
    private double atsScore;

    @Field("checks")
    private List<AtsCheckResult> checks;

    @CreatedDate
    @Field("assessedAt")
    private Instant assessedAt;

    protected AtsAssessment() {
        // Spring Data
    }

    public AtsAssessment(String jdOptimizationId, String userId, String jobDescriptionId,
                         double atsScore, List<AtsCheckResult> checks) {
        this.jdOptimizationId = jdOptimizationId;
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.atsScore = atsScore;
        this.checks = List.copyOf(checks);
    }

    public String id() {
        return id;
    }

    public String jdOptimizationId() {
        return jdOptimizationId;
    }

    public String userId() {
        return userId;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public double atsScore() {
        return atsScore;
    }

    public List<AtsCheckResult> checks() {
        return checks;
    }

    public Instant assessedAt() {
        return assessedAt;
    }
}
