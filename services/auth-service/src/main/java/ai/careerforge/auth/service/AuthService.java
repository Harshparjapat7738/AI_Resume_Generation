package ai.careerforge.auth.service;

import ai.careerforge.auth.domain.RefreshToken;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.domain.UserStatus;
import ai.careerforge.auth.repository.RefreshTokenRepository;
import ai.careerforge.auth.repository.UserRepository;
import ai.careerforge.auth.security.JwtIssuer;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    public record Tokens(String accessToken, long expiresIn, String rawRefreshToken, long refreshExpiresIn) {
    }

    public User register(String email, String rawPassword, String displayName) {
        String normalisedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalisedEmail)) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email already exists.");
        }
        User user = new User(normalisedEmail, passwordEncoder.encode(rawPassword), displayName.trim());
        return users.save(user);
    }

    public Tokens login(String email, String rawPassword) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid email or password."));

        if (user.status() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "This account is not active.");
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            user.recordFailedLogin();
            users.save(user);
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid email or password.");
        }

        user.recordSuccessfulLogin(Instant.now());
        users.save(user);

        return issueTokenPair(user.id(), UUID.randomUUID().toString(), null);
    }

    /**
     * Rotates a presented refresh token. Presenting a token that was already rotated
     * revokes the whole family (docs/DATABASE.md &sect;3 — reuse means theft).
     */
    public Tokens refresh(String rawRefreshToken) {
        String hash = jwtIssuer.hash(rawRefreshToken);
        RefreshToken presented = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "The refresh token is invalid."));

        if (presented.revokedAt() != null) {
            revokeFamily(presented.familyId(), "REFRESH_REUSE");
            throw new ApiException(ErrorCode.AUTH_REQUIRED,
                    "The refresh token has already been used. All sessions in this family were revoked.");
        }
        if (!presented.isActive(Instant.now())) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "The refresh token has expired.");
        }

        presented.revoke("ROTATED", Instant.now());
        refreshTokens.save(presented);

        User user = users.findById(presented.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "The account no longer exists."));

        return issueTokenPair(user.id(), presented.familyId(), hash);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(jwtIssuer.hash(rawRefreshToken))
                .ifPresent(token -> revokeFamily(token.familyId(), "LOGOUT"));
    }

    public User requireUser(String userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Mints a fresh token pair (new refresh-token family) for an already-authenticated
     *  user — the same path {@link #login} uses after a password check, reused by
     *  {@code GoogleOAuthService} after it has verified a Google ID token instead. */
    public Tokens issueSessionTokens(String userId) {
        return issueTokenPair(userId, UUID.randomUUID().toString(), null);
    }

    private void revokeFamily(String familyId, String reason) {
        Instant now = Instant.now();
        refreshTokens.findByFamilyId(familyId).forEach(token -> {
            if (token.revokedAt() == null) {
                token.revoke(reason, now);
                refreshTokens.save(token);
            }
        });
    }

    private Tokens issueTokenPair(String userId, String familyId, String previousTokenHash) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        JwtIssuer.AccessToken access = jwtIssuer.issueAccessToken(userId, user.roles());

        String rawRefresh = jwtIssuer.issueRefreshTokenValue();
        Instant now = Instant.now();
        RefreshToken refreshToken = new RefreshToken(
                userId, jwtIssuer.hash(rawRefresh), familyId, previousTokenHash,
                now, now.plusSeconds(jwtIssuer.refreshExpirationSeconds()));
        refreshTokens.save(refreshToken);

        return new Tokens(access.value(), access.expiresInSeconds(), rawRefresh, jwtIssuer.refreshExpirationSeconds());
    }
}
