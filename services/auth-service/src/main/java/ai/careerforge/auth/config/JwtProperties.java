package ai.careerforge.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mirrors {@code careerforge.jwt.*} on the gateway (docs/ARCHITECTURE_DECISIONS.md
 * ADR-007): the signing secret lives in exactly two places, here and the gateway, so a
 * token minted here verifies there.
 *
 * <p>{@code issuer}/{@code accessExpirationSeconds}/{@code refreshExpirationSeconds} are
 * normally supplied by config-server's shared {@code application.yml} (only {@code secret}
 * is set locally — see below). That import is deliberately {@code optional:configserver:...}
 * so a service can still boot without config-server (local IDE "Mode 2" dev per
 * docker-compose.yml). The failure mode of "optional" is silent: an unreachable
 * config-server at startup previously left these fields at their unbound defaults
 * (0 for the {@code long}s, {@code null} for {@code issuer}) with no error — every access
 * token was minted with {@code exp == iat}, i.e. already expired, and auth was silently
 * broken. {@code @Validated} turns that into a loud startup failure instead: better to
 * refuse to start than to run an auth service that hands out useless tokens.
 *
 * @param secret                    &gt;= 32 bytes, HMAC signing key (JWT_SECRET)
 * @param issuer                    must match the gateway's expected issuer
 * @param accessExpirationSeconds   access token lifetime
 * @param refreshExpirationSeconds  refresh token lifetime
 */
@ConfigurationProperties(prefix = "careerforge.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @Positive long accessExpirationSeconds,
        @Positive long refreshExpirationSeconds) {
}
