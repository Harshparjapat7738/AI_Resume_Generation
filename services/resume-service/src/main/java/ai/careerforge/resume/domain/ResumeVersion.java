package ai.careerforge.resume.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Immutable — one per successful generation (docs/DATABASE.md &sect;3). {@code content} and
 * {@code grounding} are stored as plain maps (mirroring ai-service's JSON exactly) rather
 * than a fully-typed model, since resume-service treats them as opaque, already-validated
 * payloads it persists and republishes — it never inspects their internals.
 */
@Document(collection = "resume_versions")
public class ResumeVersion {

    @Id
    private String id;

    @Field("resumeGenerationId")
    private String resumeGenerationId;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    /** Denormalised from the JD analysis at generation time, so a dashboard list doesn't
     *  need to cross-call jd-service for every row. */
    @Field("jobTitle")
    private String jobTitle;

    @Field("company")
    private String company;

    @Field("version")
    private int version;

    @Field("templateId")
    private String templateId;

    @Field("templateVersion")
    private String templateVersion;

    @Field("content")
    private Map<String, Object> content;

    @Field("evidenceMatches")
    private List<Map<String, Object>> evidenceMatches;

    @Field("gaps")
    private List<Map<String, Object>> gaps;

    @Field("groundingReport")
    private Map<String, Object> groundingReport;

    @Field("removedSections")
    private List<String> removedSections;

    @Field("promptVersion")
    private String promptVersion;

    @Field("modelId")
    private String modelId;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected ResumeVersion() {
        // Spring Data
    }

    public ResumeVersion(String resumeGenerationId, String userId, String jobDescriptionId, String jobTitle,
                         String company, int version, String templateId, String templateVersion,
                         Map<String, Object> content,
                         List<Map<String, Object>> evidenceMatches, List<Map<String, Object>> gaps,
                         Map<String, Object> groundingReport, List<String> removedSections,
                         String promptVersion, String modelId) {
        this.resumeGenerationId = resumeGenerationId;
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.version = version;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.content = content;
        this.evidenceMatches = evidenceMatches;
        this.gaps = gaps;
        this.groundingReport = groundingReport;
        this.removedSections = removedSections;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
    }

    public String id() {
        return id;
    }

    public String resumeGenerationId() {
        return resumeGenerationId;
    }

    public String userId() {
        return userId;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public String jobTitle() {
        return jobTitle;
    }

    public String company() {
        return company;
    }

    public String templateId() {
        return templateId;
    }

    public String templateVersion() {
        return templateVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> content() {
        return content;
    }

    public List<Map<String, Object>> evidenceMatches() {
        return evidenceMatches;
    }

    public List<Map<String, Object>> gaps() {
        return gaps;
    }

    public Map<String, Object> groundingReport() {
        return groundingReport;
    }

    public List<String> removedSections() {
        return removedSections;
    }
}
