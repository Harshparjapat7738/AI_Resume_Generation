package ai.careerforge.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code careerforge.oauth.google.*} (application.yml), sourced from the
 * {@code GOOGLE_*} env vars documented in docs/EXTERNAL_APIS.md. Google sign-in is optional
 * — email/password keeps working with these unset — so callers must check
 * {@link #isConfigured()} rather than assume a blank client secret is a bug.
 */
@ConfigurationProperties(prefix = "careerforge.oauth.google")
public record GoogleOAuthProperties(
        String clientId, String clientSecret, String redirectUri, String frontendBaseUrl) {

    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
