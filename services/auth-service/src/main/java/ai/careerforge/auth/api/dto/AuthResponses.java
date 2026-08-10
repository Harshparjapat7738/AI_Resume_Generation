package ai.careerforge.auth.api.dto;

import java.util.List;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record AuthResponse(String accessToken, long expiresIn) {
    }

    public record MeResponse(String userId, String email, String displayName, List<String> roles) {
    }

    public record RegisterResponse(String userId, String email) {
    }
}
