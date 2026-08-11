package ai.careerforge.auth.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A registered account. {@code passwordHash} never leaves this class — controllers map to
 * DTOs and never serialise this type directly (docs/CODEBASE.md &sect;4).
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Field("email")
    private String email;

    @Field("passwordHash")
    private String passwordHash;

    @Field("displayName")
    private String displayName;

    @Field("emailVerified")
    private boolean emailVerified = false;

    @Field("roles")
    private List<String> roles = List.of("ROLE_USER");

    @Field("status")
    private UserStatus status = UserStatus.ACTIVE;

    @Field("failedLoginAttempts")
    private int failedLoginAttempts = 0;

    @Field("lockedUntil")
    private Instant lockedUntil;

    @Field("lastLoginAt")
    private Instant lastLoginAt;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Version
    @Field("version")
    private Long version;

    @Field("schemaVersion")
    private int schemaVersion = 1;

    protected User() {
        // Spring Data
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> roles() {
        return roles;
    }

    public UserStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void recordSuccessfulLogin(Instant when) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = when;
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    /** Set when an identity provider (Google) has already verified ownership of this
     *  mailbox — never set from a client-supplied flag. */
    public void markEmailVerified() {
        this.emailVerified = true;
    }
}
