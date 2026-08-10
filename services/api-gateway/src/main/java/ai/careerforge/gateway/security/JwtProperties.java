package ai.careerforge.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT verification settings. The secret is supplied via the {@code JWT_SECRET} environment
 * variable and must never be hard-coded or committed.
 *
 * @param secret     HMAC signing key, minimum 32 bytes
 * @param issuer     expected {@code iss} claim
 * @param publicPaths ant-style paths that bypass authentication entirely
 */
@ConfigurationProperties(prefix = "careerforge.jwt")
public record JwtProperties(String secret, String issuer, List<String> publicPaths) {

    public JwtProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
