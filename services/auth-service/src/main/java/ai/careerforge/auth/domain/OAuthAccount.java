package ai.careerforge.auth.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Links a {@link User} to an external identity provider account (docs/DATABASE.md &sect;3).
 * The provider's own access/refresh tokens are never stored here for plain sign-in —
 * CareerForge issues its own session immediately after verifying the ID token
 * (docs/EXTERNAL_APIS.md "Google OAuth 2.0" &sect;Security).
 */
@Document(collection = "oauth_accounts")
public class OAuthAccount {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("provider")
    private OAuthProvider provider;

    /** The provider's stable subject identifier (Google's ID token {@code sub} claim) —
     *  never the email, which a user can change at the provider. */
    @Field("providerUserId")
    private String providerUserId;

    @Field("emailAtProvider")
    private String emailAtProvider;

    @CreatedDate
    @Field("linkedAt")
    private Instant linkedAt;

    protected OAuthAccount() {
        // Spring Data
    }

    public OAuthAccount(String userId, OAuthProvider provider, String providerUserId, String emailAtProvider) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.emailAtProvider = emailAtProvider;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public OAuthProvider provider() {
        return provider;
    }

    public String providerUserId() {
        return providerUserId;
    }

    public String emailAtProvider() {
        return emailAtProvider;
    }

    public Instant linkedAt() {
        return linkedAt;
    }
}
