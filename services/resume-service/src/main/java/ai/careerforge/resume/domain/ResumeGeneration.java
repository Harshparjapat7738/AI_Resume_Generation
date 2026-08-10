package ai.careerforge.resume.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3. <strong>Deviation (see ARCHITECTURE_DECISIONS.md ADR-013):</strong>
 * generation runs synchronously within the request in this milestone slice rather than via a
 * Redis Streams job queue, so this document is written once, already in its terminal state,
 * rather than progressing through {@code QUEUED -&gt; ... -&gt; COMPLETED}.
 */
@Document(collection = "resume_generations")
public class ResumeGeneration {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("status")
    private ResumeGenerationStatus status;

    @Field("failureCode")
    private String failureCode;

    @CreatedDate
    @Field("startedAt")
    private Instant startedAt;

    @Field("completedAt")
    private Instant completedAt;

    protected ResumeGeneration() {
        // Spring Data
    }

    public ResumeGeneration(String userId, String jobDescriptionId) {
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.status = ResumeGenerationStatus.GENERATING;
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

    public void complete() {
        this.status = ResumeGenerationStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String failureCode) {
        this.status = ResumeGenerationStatus.FAILED;
        this.failureCode = failureCode;
        this.completedAt = Instant.now();
    }
}
