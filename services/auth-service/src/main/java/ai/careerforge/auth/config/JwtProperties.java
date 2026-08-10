package ai.careerforge.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mirrors {@code careerforge.jwt.*} on the gateway (docs/ARCHITECTURE_DECISIONS.md
 * ADR-007): the signing secret lives in exactly two places, here and the gateway, so a
 * token minted here verifies there.
 *
 * @param secret                    &gt;= 32 bytes, HMAC signing key (JWT_SECRET)
 * @param issuer                    must match the gateway's expected issuer
 * @param accessExpirationSeconds   access token lifetime
 * @param refreshExpirationSeconds  refresh token lifetime
 */
@ConfigurationProperties(prefix = "careerforge.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long accessExpirationSeconds,
        long refreshExpirationSeconds) {
}
