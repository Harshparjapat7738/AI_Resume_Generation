package ai.careerforge.gateway.filter;

import ai.careerforge.gateway.security.JwtProperties;
import ai.careerforge.gateway.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies the bearer access token and propagates the caller's identity to downstream
 * services as trusted headers.
 *
 * <p>Security notes:
 * <ul>
 *   <li>Inbound {@code X-User-Id} / {@code X-User-Roles} headers are always stripped before
 *       being re-set, so a client cannot spoof another user's identity.</li>
 *   <li>Identity propagation is a convenience, not the authorisation boundary. Each service
 *       must still verify that the requested resource belongs to the caller (see IDOR/BOLA
 *       rules in docs/CODEBASE.md).</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtVerifier jwtVerifier;
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier, JwtProperties properties) {
        this.jwtVerifier = jwtVerifier;
        this.publicPaths = properties.publicPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripIdentityHeaders(exchange)).build());
        }

        Optional<String> token = bearerToken(exchange);
        if (token.isEmpty()) {
            return unauthorized(exchange, "AUTH_TOKEN_MISSING", "Authentication is required.");
        }

        Optional<Claims> claims = jwtVerifier.verify(token.get());
        if (claims.isEmpty()) {
            return unauthorized(exchange, "AUTH_TOKEN_INVALID", "The access token is invalid or expired.");
        }

        Claims verified = claims.get();
        ServerHttpRequest request = stripIdentityHeaders(exchange).mutate()
                .header(USER_ID_HEADER, verified.getSubject())
                .header(USER_ROLES_HEADER, String.join(",", roles(verified)))
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    private ServerHttpRequest stripIdentityHeaders(ServerWebExchange exchange) {
        return exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                })
                .build();
    }

    private Optional<String> bearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String value = header.substring(7).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> roles(Claims claims) {
        Object raw = claims.get("roles");
        return raw instanceof List<?> list ? (List<String>) list : List.of("ROLE_USER");
    }

    /** Emits the platform-standard error envelope; never leaks token or stack-trace detail. */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"timestamp":"%s","status":401,"code":"%s","message":"%s","path":"%s"}"""
                .formatted(Instant.now(), code, message, exchange.getRequest().getPath().value());

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
