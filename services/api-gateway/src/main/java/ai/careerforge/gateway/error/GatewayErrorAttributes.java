package ai.careerforge.gateway.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Renders gateway-level failures in the platform-standard error envelope and suppresses
 * stack traces, internal paths and exception class names.
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> defaults = super.getErrorAttributes(request, options);
        int status = (int) defaults.getOrDefault("status", 500);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("code", codeFor(status));
        body.put("message", messageFor(status));
        body.put("path", request.path());
        return body;
    }

    private String codeFor(int status) {
        return switch (status) {
            case 401 -> "AUTH_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "ROUTE_NOT_FOUND";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "GATEWAY_ERROR";
        };
    }

    private String messageFor(int status) {
        return switch (status) {
            case 401 -> "Authentication is required.";
            case 403 -> "You do not have access to this resource.";
            case 404 -> "The requested route does not exist.";
            case 429 -> "Too many requests. Please retry shortly.";
            case 503 -> "The requested service is temporarily unavailable.";
            default -> HttpStatus.valueOf(status).getReasonPhrase();
        };
    }
}
