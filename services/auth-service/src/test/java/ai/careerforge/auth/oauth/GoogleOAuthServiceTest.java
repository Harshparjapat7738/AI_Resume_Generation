package ai.careerforge.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.auth.config.GoogleOAuthProperties;
import ai.careerforge.auth.domain.OAuthAccount;
import ai.careerforge.auth.domain.OAuthProvider;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.repository.OAuthAccountRepository;
import ai.careerforge.auth.repository.SecurityEventRepository;
import ai.careerforge.auth.repository.UserRepository;
import ai.careerforge.auth.service.AuthService;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    private static final GoogleOAuthProperties CONFIGURED = new GoogleOAuthProperties(
            "client-id", "client-secret", "http://localhost:8080/api/auth/oauth2/callback/google",
            "http://localhost:5173");
    private static final GoogleOAuthProperties NOT_CONFIGURED =
            new GoogleOAuthProperties(null, null, null, "http://localhost:5173");

    @Mock private GoogleOAuthClient client;
    @Mock private OAuthStateStore stateStore;
    @Mock private UserRepository users;
    @Mock private OAuthAccountRepository oauthAccounts;
    @Mock private SecurityEventRepository securityEvents;
    @Mock private AuthService authService;

    private GoogleOAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthService(CONFIGURED, client, stateStore, users, oauthAccounts, securityEvents, authService);
    }

    @Test
    void beginAuthorizationStoresThePkceVerifierAndReturnsGoogleUrl() {
        when(client.buildAuthorizationUrl(anyString(), anyString())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?...");

        String url = service.beginAuthorization();

        assertThat(url).startsWith("https://accounts.google.com/");
        verify(stateStore).store(anyString(), anyString());
    }

    @Test
    void beginAuthorizationFailsCleanlyWhenGoogleSignInIsNotConfigured() {
        GoogleOAuthService disabled = new GoogleOAuthService(
                NOT_CONFIGURED, client, stateStore, users, oauthAccounts, securityEvents, authService);

        assertThatThrownBy(disabled::beginAuthorization)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);

        verify(client, never()).buildAuthorizationUrl(any(), any());
    }

    @Test
    void callbackWithMissingCodeOrStateFailsBeforeTouchingTheStateStoreOrGoogle() {
        assertThatThrownBy(() -> service.handleCallback(null, "state-1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);

        verify(stateStore, never()).consume(any());
    }

    @Test
    void callbackWithAnUnknownOrExpiredStateFailsBeforeCallingGoogle() {
        when(stateStore.consume("state-1")).thenReturn(null);

        assertThatThrownBy(() -> service.handleCallback("code-1", "state-1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);

        verify(client, never()).exchangeCodeForIdentity(any(), any());
    }

    @Test
    void callbackRejectsAnUnverifiedGoogleEmailWithoutCreatingOrLinkingAnyAccount() {
        when(stateStore.consume("state-1")).thenReturn("verifier-1");
        when(client.exchangeCodeForIdentity("code-1", "verifier-1"))
                .thenReturn(new GoogleIdentity("google-sub-1", "ada@example.com", false, "Ada"));

        assertThatThrownBy(() -> service.handleCallback("code-1", "state-1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);

        verify(users, never()).save(any());
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void callbackForABrandNewIdentityCreatesAVerifiedOAuthOnlyUserAndLinksIt() {
        when(stateStore.consume("state-1")).thenReturn("verifier-1");
        when(client.exchangeCodeForIdentity("code-1", "verifier-1"))
                .thenReturn(new GoogleIdentity("google-sub-1", "ada@example.com", true, "Ada Lovelace"));
        when(oauthAccounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authService.issueSessionTokens(any())).thenReturn(
                new AuthService.Tokens("access-token", 900, "refresh-token", 1_209_600));

        AuthService.Tokens tokens = service.handleCallback("code-1", "state-1");

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        verify(users).save(argThatUserHasEmail("ada@example.com"));
        verify(oauthAccounts).save(any(OAuthAccount.class));
        verify(securityEvents, times(1)).save(any());
    }

    @Test
    void callbackForAnAlreadyLinkedIdentityReusesTheExistingUserWithoutCreatingAnything() {
        when(stateStore.consume("state-1")).thenReturn("verifier-1");
        when(client.exchangeCodeForIdentity("code-1", "verifier-1"))
                .thenReturn(new GoogleIdentity("google-sub-1", "ada@example.com", true, "Ada"));

        OAuthAccount existingLink = new OAuthAccount("existing-user-1", OAuthProvider.GOOGLE, "google-sub-1", "ada@example.com");
        when(oauthAccounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(existingLink));
        when(users.findById("existing-user-1")).thenReturn(Optional.of(userWithId("existing-user-1", "ada@example.com")));
        when(authService.issueSessionTokens("existing-user-1")).thenReturn(
                new AuthService.Tokens("access-token", 900, "refresh-token", 1_209_600));

        service.handleCallback("code-1", "state-1");

        verify(users, never()).save(any());
        verify(oauthAccounts, never()).save(any());
        verify(authService).issueSessionTokens("existing-user-1");
    }

    private static User argThatUserHasEmail(String email) {
        return org.mockito.ArgumentMatchers.argThat(u -> u != null && email.equals(u.email()));
    }

    /** {@code User.id()} is only ever populated by Spring Data on save/retrieval — a plain
     *  {@code new User(...)} has a null id, which would silently break any assertion keyed
     *  on it, so fixtures that stand in for "a user already in the database" set it via
     *  reflection, matching what a real repository read would actually return. */
    private static User userWithId(String id, String email) {
        User user = new User(email, "hash", "Display Name");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return user;
    }
}
