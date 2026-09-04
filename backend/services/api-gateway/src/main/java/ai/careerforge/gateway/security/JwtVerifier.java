package ai.careerforge.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies access tokens issued by auth-service.
 *
 * <p>Verification only — the gateway never mints or refreshes tokens. Failures are logged
 * without the token value, per the logging rules in docs/CODEBASE.md.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "careerforge.jwt.secret (JWT_SECRET) is missing or shorter than 32 bytes");
        }
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            // Normally supplied by config-server's shared application.yml — an unreachable
            // config-server at startup (the import is `optional:configserver:...`, see
            // docker-compose.yml) leaves this unbound. Every request would otherwise be
            // rejected one-by-one with an unhelpful per-request log line; failing loudly at
            // startup instead makes a broken deployment obvious immediately.
            throw new IllegalStateException(
                    "careerforge.jwt.issuer is missing — is config-server reachable and healthy?");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
    }

    /**
     * @return the verified claims, or empty if the token is absent, malformed, expired,
     *         wrongly issued or wrongly signed
     */
    public Optional<Claims> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected access token: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
