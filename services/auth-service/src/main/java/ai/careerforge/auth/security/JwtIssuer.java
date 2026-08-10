package ai.careerforge.auth.security;

import ai.careerforge.auth.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Mints access tokens and opaque refresh tokens.
 *
 * <p>The signing key is derived exactly as the gateway's {@code JwtVerifier} derives it
 * (raw UTF-8 bytes of {@code JWT_SECRET} via {@code Keys.hmacShaKeyFor}), so a token minted
 * here verifies there without any shared code between the two services (ADR-007: the secret
 * is the only thing that has to match).
 */
@Component
public class JwtIssuer {

    private final SecretKey key;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public JwtIssuer(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "careerforge.jwt.secret (JWT_SECRET) is missing or shorter than 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken issueAccessToken(String userId, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.accessExpirationSeconds());
        String token = Jwts.builder()
                .subject(userId)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("roles", roles)
                .signWith(key)
                .compact();
        return new AccessToken(token, properties.accessExpirationSeconds());
    }

    /** A cryptographically random opaque string — never a JWT, never predictable. */
    public String issueRefreshTokenValue() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long refreshExpirationSeconds() {
        return properties.refreshExpirationSeconds();
    }

    /** Refresh tokens are stored only as a SHA-256 hash (docs/DATABASE.md &sect;3). */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
