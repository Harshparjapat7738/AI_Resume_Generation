package ai.careerforge.jd.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3. Only {@code sourceType = TEXT} is implemented in this milestone
 * slice — file upload and URL fetch (with the SSRF guard) are documented but deferred.
 */
@Document(collection = "job_descriptions")
public class JobDescription {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("sourceType")
    private String sourceType;

    @Field("title")
    private String title;

    @Field("company")
    private String company;

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
        this.userId = userId;
        this.sourceType = sourceType;
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

    public String title() {
        return title;
    }

    public String company() {
        return company;
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

    public void confirm() {
        this.status = JobDescriptionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        this.confirmedVersion = currentVersion;
    }
}
