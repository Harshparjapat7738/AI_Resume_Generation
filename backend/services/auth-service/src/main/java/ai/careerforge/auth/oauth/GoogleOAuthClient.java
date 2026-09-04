package ai.careerforge.auth.oauth;

import ai.careerforge.auth.config.GoogleOAuthProperties;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The only class that talks to Google's OAuth endpoints. Mirrors {@code GroqClient}'s role
 * as "the one place an external API is called from" — request/response bodies (which never
 * contain more than an authorization code or a token) are not logged, and every failure
 * mode is translated into the platform's own error envelope rather than leaking a raw
 * Google error to the client.
 */
@Component
class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;
    private final JwtDecoder idTokenDecoder;

    GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .requestFactory(timeoutBoundedRequestFactory())
                .build();
        this.idTokenDecoder = buildIdTokenDecoder(properties);
    }

    /** Where the browser is sent to reach Google's consent screen. */
    String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "online")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    /** Exchanges the authorization code for tokens, then verifies the ID token's signature,
     *  issuer, audience and expiry before trusting anything it claims. */
    GoogleIdentity exchangeCodeForIdentity(String code, String codeVerifier) {
        GoogleTokenResponse tokenResponse = exchangeCode(code, codeVerifier);
        Jwt idToken = decodeAndVerify(tokenResponse.id_token());

        Boolean emailVerified = idToken.getClaim("email_verified");
        return new GoogleIdentity(
                idToken.getSubject(),
                idToken.getClaimAsString("email"),
                Boolean.TRUE.equals(emailVerified),
                idToken.getClaimAsString("name"));
    }

    private GoogleTokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);

        GoogleTokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientException ex) {
            log.warn("Google token exchange failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Google sign-in is temporarily unavailable.", ex);
        }

        if (response == null || response.id_token() == null || response.id_token().isBlank()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Google sign-in is temporarily unavailable.");
        }
        return response;
    }

    private Jwt decodeAndVerify(String rawIdToken) {
        try {
            return idTokenDecoder.decode(rawIdToken);
        } catch (JwtException ex) {
            log.warn("Google ID token failed verification: {}", ex.getMessage());
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "Google sign-in could not be verified.");
        }
    }

    private static NimbusJwtDecoder buildIdTokenDecoder(GoogleOAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(ISSUER);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(properties.clientId())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Unexpected audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private static SimpleClientHttpRequestFactory timeoutBoundedRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return factory;
    }

    /** Field names match Google's JSON verbatim (snake_case), same convention as
     *  ai-service's GroqMessages — no Jackson naming-strategy config needed. Google's own
     *  access/refresh tokens are read here but never persisted or returned to a caller
     *  (docs/EXTERNAL_APIS.md: not stored for plain sign-in). */
    private record GoogleTokenResponse(String access_token, String id_token, String token_type, Integer expires_in) {
    }
}
