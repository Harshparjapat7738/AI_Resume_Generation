package ai.careerforge.auth.api;

import ai.careerforge.auth.api.dto.AuthRequests.LoginRequest;
import ai.careerforge.auth.api.dto.AuthRequests.RegisterRequest;
import ai.careerforge.auth.api.dto.AuthResponses.AuthResponse;
import ai.careerforge.auth.api.dto.AuthResponses.MeResponse;
import ai.careerforge.auth.api.dto.AuthResponses.RegisterResponse;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.oauth.GoogleOAuthService;
import ai.careerforge.auth.service.AuthService;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.security.CallerId;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** See docs/API_CATALOG.md &sect;2 (auth-service). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;

    @Value("${careerforge.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    public AuthController(AuthService authService, GoogleOAuthService googleOAuthService) {
        this.authService = authService;
        this.googleOAuthService = googleOAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.id(), user.email()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthService.Tokens tokens = authService.login(request.email(), request.password());
        setRefreshCookie(response, tokens.rawRefreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthService.Tokens tokens = authService.refresh(refreshCookie);
        setRefreshCookie(response, tokens.rawRefreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CallerId String userId,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            HttpServletResponse response) {
        authService.logout(refreshCookie);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@CallerId String userId) {
        User user = authService.requireUser(userId);
        return ResponseEntity.ok(new MeResponse(user.id(), user.email(), user.displayName(), user.roles()));
    }

    /** Begins Authorization Code + PKCE (docs/EXTERNAL_APIS.md "Google OAuth 2.0"). A full
     *  browser navigation, not a fetch call — the response is a redirect, never JSON. */
    @GetMapping("/oauth2/authorize/google")
    public ResponseEntity<Void> authorizeGoogle() {
        if (!googleOAuthService.isEnabled()) {
            URI failureRedirect = URI.create(googleOAuthService.frontendRedirectUrl() + "/login?error=google_oauth");
            return ResponseEntity.status(HttpStatus.FOUND).location(failureRedirect).build();
        }
        String authorizationUrl = googleOAuthService.beginAuthorization();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizationUrl)).build();
    }

    /** Google redirects the browser here with either {@code code}+{@code state} or an
     *  {@code error} (e.g. the user declined consent). Either way this ends in a redirect
     *  back to the frontend — never a JSON error a browser navigation can't do anything
     *  with — the refresh cookie is set first on success, exactly as {@link #login} sets it,
     *  so the frontend's existing session bootstrap (a GET /api/auth/refresh on load) picks
     *  it up with no OAuth-specific frontend code at all. */
    @GetMapping("/oauth2/callback/google")
    public ResponseEntity<Void> callbackGoogle(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) {
        String frontendBase = googleOAuthService.frontendRedirectUrl();
        URI failureRedirect = URI.create(frontendBase + "/login?error=google_oauth");
        if (error != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(failureRedirect).build();
        }
        AuthService.Tokens tokens;
        try {
            tokens = googleOAuthService.handleCallback(code, state);
        } catch (ApiException ex) {
            // Never let this become the platform's usual JSON error envelope — this request
            // is a full browser navigation from Google, not a fetch a frontend can catch.
            return ResponseEntity.status(HttpStatus.FOUND).location(failureRedirect).build();
        }
        setRefreshCookie(response, tokens.rawRefreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendBase + "/")).build();
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
