package ai.careerforge.auth.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One link in a refresh-token rotation chain. The raw token is never stored — only its
 * SHA-256 hash. Presenting an already-rotated ({@code revokedReason = ROTATED}) token again
 * means theft: the whole {@code familyId} is revoked (docs/DATABASE.md &sect;3).
 */
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("tokenHash")
    private String tokenHash;

    @Field("familyId")
    private String familyId;

    @Field("previousTokenHash")
    private String previousTokenHash;

    @Field("issuedAt")
    private Instant issuedAt;

    @Field("expiresAt")
    private Instant expiresAt;

    @Field("revokedAt")
    private Instant revokedAt;

    @Field("revokedReason")
    private String revokedReason;

    protected RefreshToken() {
        // Spring Data
    }

    public RefreshToken(String userId, String tokenHash, String familyId,
                        String previousTokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.previousTokenHash = previousTokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String familyId() {
        return familyId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String revokedReason() {
        return revokedReason;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(String reason, Instant when) {
        this.revokedAt = when;
        this.revokedReason = reason;
    }
}
