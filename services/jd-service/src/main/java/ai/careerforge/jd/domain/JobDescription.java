package ai.careerforge.jd.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3. {@code sourceType} is {@code TEXT} or {@code URL} — file upload
 * remains deferred. For {@code URL}, {@code title}/{@code company}/{@code location}/
 * {@code skillsSummary}/{@code experienceSummary} are populated at fetch time only when the
 * source page embeds schema.org {@code JobPosting} structured data; otherwise they stay
 * null and the user reviews the extracted text directly, same as a pasted JD.
 */
@Document(collection = "job_descriptions")
public class JobDescription {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("sourceType")
    private String sourceType;

    @Field("sourceUrl")
    private String sourceUrl;

    @Field("title")
    private String title;

    @Field("company")
    private String company;

    @Field("location")
    private String location;

    @Field("skillsSummary")
    private String skillsSummary;

    @Field("experienceSummary")
    private String experienceSummary;

    @Field("status")
    private JobDescriptionStatus status;

    @Field("currentVersion")
    private int currentVersion;

    @Field("confirmedAt")
    private Instant confirmedAt;

    @Field("confirmedVersion")
    private Integer confirmedVersion;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Version
    @Field("version")
    private Long entityVersion;

    protected JobDescription() {
        // Spring Data
    }

    public JobDescription(String userId, String sourceType) {
        this(userId, sourceType, null);
    }

    public JobDescription(String userId, String sourceType, String sourceUrl) {
        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.status = JobDescriptionStatus.EXTRACTED;
        this.currentVersion = 1;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String title() {
        return title;
    }

    public String company() {
        return company;
    }

    public String location() {
        return location;
    }

    public String skillsSummary() {
        return skillsSummary;
    }

    public String experienceSummary() {
        return experienceSummary;
    }

    public JobDescriptionStatus status() {
        return status;
    }

    public int currentVersion() {
        return currentVersion;
    }

    public Integer confirmedVersion() {
        return confirmedVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void applyAnalysedTitleAndCompany(String title, String company) {
        this.title = title;
        this.company = company;
    }

    /** Populated at fetch time from schema.org JSON-LD when the source page provides it —
     *  see {@link ai.careerforge.jd.fetch.JobPostingExtractor}. Never overwrites a title or
     *  company already set by AI analysis; this only ever runs before that, at intake. */
    public void applyUrlExtractionPreview(String title, String company, String location,
                                          String skillsSummary, String experienceSummary) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.skillsSummary = skillsSummary;
        this.experienceSummary = experienceSummary;
    }

    public void confirm() {
        this.status = JobDescriptionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        this.confirmedVersion = currentVersion;
    }
}
