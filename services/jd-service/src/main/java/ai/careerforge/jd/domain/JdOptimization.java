package ai.careerforge.jd.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The JD-optimization result (ADR-033) — the product's primary deliverable now that CareerForge
 * no longer generates a resume or cover letter.
 *
 * <p>Keyed by {@code jdVersionId}, not {@code jobDescriptionId}, for the same reason
 * {@link JdAnalysis} is: an edited job description produces a new version, and an optimization
 * computed against the old text no longer describes the new one. Reusing the analysis's own key
 * also means the two always agree about which text they were derived from.
 *
 * <p>{@code optimisation} is stored as a plain map, mirroring ai-service's validated JSON
 * exactly — jd-service treats it as an opaque, already-verified payload it persists and
 * republishes, the same choice {@code ResumeVersion} made for generated content. Nothing here
 * duplicates the JD or the profile: {@code citedEvidenceIds} holds ids, not evidence, and the
 * requirement text lives in {@link JdAnalysis} where it already did.
 */
@Document(collection = "jd_optimizations")
public class JdOptimization {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("jdVersionId")
    private String jdVersionId;

    @Field("optimisation")
    private Map<String, Object> optimisation;

    /** Every evidence id the result cites — the provenance trail proving each candidate-facing
     *  value traces back to the user's own profile. */
    @Field("citedEvidenceIds")
    private List<String> citedEvidenceIds = List.of();

    @Field("promptVersion")
    private String promptVersion;

    @Field("modelId")
    private String modelId;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected JdOptimization() {
        // Spring Data
    }

    public JdOptimization(String userId, String jobDescriptionId, String jdVersionId,
                          Map<String, Object> optimisation, List<String> citedEvidenceIds,
                          String promptVersion, String modelId) {
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.jdVersionId = jdVersionId;
        this.optimisation = optimisation;
        this.citedEvidenceIds = citedEvidenceIds == null ? List.of() : citedEvidenceIds;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
    }

    /** Replaces this row's result in place — used when the user explicitly re-optimises against
     *  the same confirmed text (e.g. after editing their profile), so there is one current
     *  optimization per JD version rather than an accumulating history. */
    public void replaceWith(Map<String, Object> optimisation, List<String> citedEvidenceIds,
                            String promptVersion, String modelId) {
        this.optimisation = optimisation;
        this.citedEvidenceIds = citedEvidenceIds == null ? List.of() : citedEvidenceIds;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
        this.createdAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public String jdVersionId() {
        return jdVersionId;
    }

    public Map<String, Object> optimisation() {
        return optimisation;
    }

    public List<String> citedEvidenceIds() {
        return citedEvidenceIds;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String modelId() {
        return modelId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
