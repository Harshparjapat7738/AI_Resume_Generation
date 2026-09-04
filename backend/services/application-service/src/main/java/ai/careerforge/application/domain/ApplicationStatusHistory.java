package ai.careerforge.application.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Append-only audit trail of {@link Application} status transitions — docs/DATABASE.md
 * &sect;3. Never updated or deleted; one row per {@code PATCH /api/applications/{id}/status}
 * call.
 */
@Document(collection = "application_status_history")
public class ApplicationStatusHistory {

    @Id
    private String id;

    @Field("applicationId")
    private String applicationId;

    @Field("userId")
    private String userId;

    @Field("fromStatus")
    private ApplicationStatus fromStatus;

    @Field("toStatus")
    private ApplicationStatus toStatus;

    @Field("note")
    private String note;

    @CreatedDate
    @Field("changedAt")
    private Instant changedAt;

    protected ApplicationStatusHistory() {
        // Spring Data
    }

    public ApplicationStatusHistory(String applicationId, String userId, ApplicationStatus fromStatus,
                                    ApplicationStatus toStatus, String note) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
    }

    public String id() {
        return id;
    }

    public String applicationId() {
        return applicationId;
    }

    public ApplicationStatus fromStatus() {
        return fromStatus;
    }

    public ApplicationStatus toStatus() {
        return toStatus;
    }

    public String note() {
        return note;
    }

    public Instant changedAt() {
        return changedAt;
    }
}
