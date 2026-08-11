package ai.careerforge.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.auth.config.JwtProperties;
import ai.careerforge.auth.domain.RefreshToken;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.domain.UserStatus;
import ai.careerforge.auth.repository.RefreshTokenRepository;
import ai.careerforge.auth.repository.UserRepository;
import ai.careerforge.auth.security.JwtIssuer;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Uses a real {@link JwtIssuer} and a low-cost real {@link BCryptPasswordEncoder} rather
 * than mocking them — both are pure, dependency-free logic, so exercising the real
 * implementation is both simpler and more meaningful than mocking it. Only the two Mongo
 * repositories (genuine I/O boundaries) are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost: fast tests
        JwtIssuer jwtIssuer = new JwtIssuer(new JwtProperties(
                "test-signing-secret-at-least-32-bytes-long!!", "test-issuer", 900, 1_209_600));
        authService = new AuthService(users, refreshTokens, passwordEncoder, jwtIssuer);
    }

    @Test
    void registerHashesThePasswordAndNeverStoresItInPlainText() {
        when(users.existsByEmail("ada@example.com")).thenReturn(false);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User saved = authService.register("Ada@Example.com  ", "correct horse battery", "Ada Lovelace");

        assertThat(saved.email()).isEqualTo("ada@example.com"); // trimmed + lowercased
        assertThat(saved.passwordHash()).isNotEqualTo("correct horse battery");
        assertThat(new BCryptPasswordEncoder(4).matches("correct horse battery", saved.passwordHash())).isTrue();
    }

    @Test
    void registerRejectsADuplicateEmailWithConflict() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("ada@example.com", "password123", "Ada"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(users, never()).save(any());
    }

    @Test
    void loginWithTheWrongPasswordFailsAndRecordsTheAttemptWithoutRevealingWhichFieldWasWrong() {
        User user = activeUserWithPassword("correct-password");
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.login("ada@example.com", "wrong-password"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR); // never 401 — see ADR-007 style non-enumeration

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginWithAnUnknownEmailFailsTheSameWayAsAWrongPassword() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", "anything"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void loginRejectsANonActiveAccount() {
        User user = activeUserWithPassword("correct-password");
        user.recordFailedLogin(); // no-op for status; just using a real mutation path
        setStatusViaReflection(user, UserStatus.LOCKED);
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("ada@example.com", "correct-password"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void successfulLoginIssuesAnAccessTokenAndAFreshRefreshTokenFamily() {
        User user = activeUserWithPassword("correct-password");
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById("user-1")).thenReturn(Optional.of(user));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.Tokens tokens = authService.login("ada@example.com", "correct-password");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.rawRefreshToken()).isNotBlank();
        assertThat(user.lastLoginAt()).isNotNull();
        assertThat(user.failedLoginAttempts()).isZero();
    }

    @Test
    void refreshRotatesTheTokenAndRevokesOnlyThePresentedOne() {
        RefreshToken presented = new RefreshToken("user-1", "hash-of-old", "family-1", null,
                Instant.now(), Instant.now().plusSeconds(3600));
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(presented));
        when(users.findById("user-1")).thenReturn(Optional.of(activeUserWithPassword("x")));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.Tokens tokens = authService.refresh("raw-old-token");

        assertThat(presented.revokedReason()).isEqualTo("ROTATED");
        assertThat(tokens.rawRefreshToken()).isNotBlank();
        verify(refreshTokens, times(2)).save(any()); // the rotated-away token, then the new one
    }

    @Test
    void reusingAnAlreadyRotatedRefreshTokenRevokesTheWholeFamilyAndFails() {
        RefreshToken alreadyRotated = new RefreshToken("user-1", "hash-of-old", "family-1", null,
                Instant.now(), Instant.now().plusSeconds(3600));
        alreadyRotated.revoke("ROTATED", Instant.now());

        RefreshToken sibling = new RefreshToken("user-1", "hash-of-sibling", "family-1", "hash-of-old",
                Instant.now(), Instant.now().plusSeconds(3600));

        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(alreadyRotated));
        when(refreshTokens.findByFamilyId("family-1")).thenReturn(java.util.List.of(alreadyRotated, sibling));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.refresh("raw-old-token"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.AUTH_REQUIRED);

        assertThat(sibling.revokedReason()).isEqualTo("REFRESH_REUSE");
    }

    @Test
    void logoutIsANoOpWithNoCookieRatherThanAnError() {
        authService.logout(null);
        authService.logout("");

        verify(refreshTokens, never()).findByTokenHash(any());
    }

    private User activeUserWithPassword(String rawPassword) {
        User user = new User("ada@example.com", new BCryptPasswordEncoder(4).encode(rawPassword), "Ada Lovelace");
        setIdViaReflection(user, "user-1");
        return user;
    }

    private static void setIdViaReflection(User user, String id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setStatusViaReflection(User user, UserStatus status) {
        try {
            var field = User.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(user, status);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
