package ai.careerforge.assessment.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3. <strong>Scope deviation — see ARCHITECTURE_DECISIONS.md
 * ADR-014:</strong> the blueprint's ten checks assume a rendered PDF/DOCX to inspect
 * (fonts, layout, header/footer safety…), which document-service doesn't produce yet. This
 * engine scores the structured resume content instead — still deterministic, still computed
 * in Java, never asked of the LLM, just scoped to what's actually measurable today.
 */
@Document(collection = "ats_assessments")
public class AtsAssessment {

    @Id
    private String id;

    @Field("resumeVersionId")
    private String resumeVersionId;

    @Field("userId")
    private String userId;

    @Field("totalScore")
    private double totalScore;

    @Field("checks")
    private List<AtsCheckResult> checks;

    @Field("engineVersion")
    private String engineVersion;

    @CreatedDate
    @Field("assessedAt")
    private Instant assessedAt;

    protected AtsAssessment() {
        // Spring Data
    }

    public AtsAssessment(String resumeVersionId, String userId, List<AtsCheckResult> checks, String engineVersion) {
        this.resumeVersionId = resumeVersionId;
        this.userId = userId;
        this.checks = List.copyOf(checks);
        this.totalScore = checks.stream().mapToDouble(AtsCheckResult::earned).sum();
        this.engineVersion = engineVersion;
    }

    public String id() {
        return id;
    }

    public String resumeVersionId() {
        return resumeVersionId;
    }

    public double totalScore() {
        return totalScore;
    }

    public List<AtsCheckResult> checks() {
        return checks;
    }

    public String engineVersion() {
        return engineVersion;
    }

    public Instant assessedAt() {
        return assessedAt;
    }
}
