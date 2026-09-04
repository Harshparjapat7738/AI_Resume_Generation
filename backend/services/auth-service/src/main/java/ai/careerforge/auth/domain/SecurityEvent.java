package ai.careerforge.auth.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Append-only audit timeline (docs/DATABASE.md &sect;3). Never updated after creation. */
@Document(collection = "security_events")
public class SecurityEvent {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("type")
    private SecurityEventType type;

    @Field("ipHash")
    private String ipHash;

    @Field("userAgentHash")
    private String userAgentHash;

    @Field("correlationId")
    private String correlationId;

    @CreatedDate
    @Field("occurredAt")
    private Instant occurredAt;

    protected SecurityEvent() {
        // Spring Data
    }

    public SecurityEvent(String userId, SecurityEventType type, String ipHash, String userAgentHash, String correlationId) {
        this.userId = userId;
        this.type = type;
        this.ipHash = ipHash;
        this.userAgentHash = userAgentHash;
        this.correlationId = correlationId;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public SecurityEventType type() {
        return type;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
