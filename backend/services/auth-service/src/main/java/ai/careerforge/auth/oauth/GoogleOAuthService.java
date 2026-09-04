package ai.careerforge.auth.oauth;

import ai.careerforge.auth.config.GoogleOAuthProperties;
import ai.careerforge.auth.domain.OAuthAccount;
import ai.careerforge.auth.domain.OAuthProvider;
import ai.careerforge.auth.domain.SecurityEvent;
import ai.careerforge.auth.domain.SecurityEventType;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.repository.OAuthAccountRepository;
import ai.careerforge.auth.repository.SecurityEventRepository;
import ai.careerforge.auth.repository.UserRepository;
import ai.careerforge.auth.service.AuthService;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Orchestrates "Sign in with Google" (docs/EXTERNAL_APIS.md "Google OAuth 2.0"): begins the
 * Authorization Code + PKCE flow, then on callback verifies the identity Google vouches for
 * and resolves it to a CareerForge account before handing off to {@link AuthService} to mint
 * this platform's own session — Google's tokens are never used as CareerForge's own
 * credential.
 */
@Service
public class GoogleOAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthClient client;
    private final OAuthStateStore stateStore;
    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final SecurityEventRepository securityEvents;
    private final AuthService authService;

    public GoogleOAuthService(GoogleOAuthProperties properties, GoogleOAuthClient client, OAuthStateStore stateStore,
                              UserRepository users, OAuthAccountRepository oauthAccounts,
                              SecurityEventRepository securityEvents, AuthService authService) {
        this.properties = properties;
        this.client = client;
        this.stateStore = stateStore;
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.securityEvents = securityEvents;
        this.authService = authService;
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    public String frontendRedirectUrl() {
        return properties.frontendBaseUrl();
    }

    /** Returns the URL to send the browser to. Generates and stores this flow's PKCE
     *  verifier, keyed by a fresh single-use state. */
    public String beginAuthorization() {
        requireEnabled();
        PkceGenerator.Pair pkce = PkceGenerator.generate();
        String state = generateState();
        stateStore.store(state, pkce.verifier());
        return client.buildAuthorizationUrl(state, pkce.challenge());
    }

    /** Completes the flow: validates {@code state}, exchanges {@code code} for a verified
     *  Google identity, resolves or creates the CareerForge account, and issues a session
     *  exactly as {@link AuthService#login} would. */
    public AuthService.Tokens handleCallback(String code, String state) {
        requireEnabled();
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "The Google sign-in flow is missing required parameters.");
        }

        String codeVerifier = stateStore.consume(state);
        if (codeVerifier == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED,
                    "This Google sign-in link has expired or was already used. Please try again.");
        }

        GoogleIdentity identity = client.exchangeCodeForIdentity(code, codeVerifier);
        if (!identity.emailVerified()) {
            // Google itself hasn't confirmed the user owns this mailbox — never establish
            // identity from an unverified claim (the same "never fabricate trust" principle
            // applied elsewhere in this codebase to AI-generated content).
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "Google reported this email address as unverified.");
        }

        User user = resolveOrCreateUser(identity);
        return authService.issueSessionTokens(user.id());
    }

    private User resolveOrCreateUser(GoogleIdentity identity) {
        Optional<OAuthAccount> existingLink =
                oauthAccounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, identity.subject());
        if (existingLink.isPresent()) {
            return users.findById(existingLink.get().userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "The linked account no longer exists."));
        }

        String normalisedEmail = identity.email() == null ? null : identity.email().trim().toLowerCase();
        User user = normalisedEmail == null ? null : users.findByEmail(normalisedEmail).orElse(null);

        if (user == null) {
            String displayName = identity.name() != null && !identity.name().isBlank() ? identity.name() : normalisedEmail;
            // passwordHash is null: an OAuth-only account (docs/DATABASE.md &sect;3).
            user = new User(normalisedEmail, null, displayName);
            user.markEmailVerified();
            user = users.save(user);
        } else if (!user.emailVerified()) {
            // A pre-existing password account whose email Google has now vouched for.
            user.markEmailVerified();
            user = users.save(user);
        }

        oauthAccounts.save(new OAuthAccount(user.id(), OAuthProvider.GOOGLE, identity.subject(), identity.email()));
        securityEvents.save(new SecurityEvent(user.id(), SecurityEventType.OAUTH_LINKED, null, null, null));
        return user;
    }

    private void requireEnabled() {
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Google sign-in is not available right now.");
        }
    }

    private static String generateState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
