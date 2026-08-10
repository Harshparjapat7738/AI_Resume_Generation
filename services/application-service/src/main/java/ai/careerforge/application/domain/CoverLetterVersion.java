package ai.careerforge.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Immutable — one per generation, versioned per {@link Application}, mirroring resume-service's
 * {@code ResumeVersion} and this service's own {@code EmailContent} (docs/DATABASE.md &sect;3).
 *
 * <p>{@code content} is stored as a plain map (mirroring ai-service's validated JSON exactly)
 * rather than a fully-typed model — application-service treats it as an opaque, already-
 * grounded payload it persists and republishes, the same choice {@code ResumeVersion} makes.
 * {@code groundingReport} and {@code removedParagraphs} travel with it so a letter remains
 * explainable, and so the UI can say plainly when a paragraph was dropped rather than shown
 * unverified.
 */
@Document(collection = "cover_letter_versions")
public class CoverLetterVersion {

    @Id
    private String id;

    @Field("applicationId")
    private String applicationId;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    /** Denormalised from the application at generation time, so a dashboard list doesn't
     *  need a cross-service call per row. */
    @Field("jobTitle")
    private String jobTitle;

    @Field("company")
    private String company;

    @Field("version")
    private int version;

    @Field("content")
    private Map<String, Object> content;

    @Field("groundingReport")
    private Map<String, Object> groundingReport;

    @Field("removedParagraphs")
    private List<String> removedParagraphs;

    @Field("promptVersion")
    private String promptVersion;

    @Field("modelId")
    private String modelId;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected CoverLetterVersion() {
        // Spring Data
    }

    public CoverLetterVersion(String applicationId, String userId, String jobDescriptionId, String jobTitle,
                              String company, int version, Map<String, Object> content,
                              Map<String, Object> groundingReport, List<String> removedParagraphs,
                              String promptVersion, String modelId) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.version = version;
        this.content = content;
        this.groundingReport = groundingReport;
        this.removedParagraphs = removedParagraphs;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
    }

    public String id() {
        return id;
    }

    public String applicationId() {
        return applicationId;
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

    public int version() {
        return version;
    }

    public Map<String, Object> content() {
        return content;
    }

    public Map<String, Object> groundingReport() {
        return groundingReport;
    }

    public List<String> removedParagraphs() {
        return removedParagraphs;
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
