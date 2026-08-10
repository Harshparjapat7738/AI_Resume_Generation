package ai.careerforge.auth.api;

import ai.careerforge.auth.api.dto.AuthRequests.LoginRequest;
import ai.careerforge.auth.api.dto.AuthRequests.RegisterRequest;
import ai.careerforge.auth.api.dto.AuthResponses.AuthResponse;
import ai.careerforge.auth.api.dto.AuthResponses.MeResponse;
import ai.careerforge.auth.api.dto.AuthResponses.RegisterResponse;
import ai.careerforge.auth.domain.User;
import ai.careerforge.auth.service.AuthService;
import ai.careerforge.common.security.CallerId;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

/** See docs/API_CATALOG.md &sect;3 (Milestone 2 — auth-service). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    @Value("${careerforge.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
