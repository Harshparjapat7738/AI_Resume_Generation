package ai.careerforge.gateway.config;

import ai.careerforge.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Key resolvers for the Redis token-bucket rate limiter.
 *
 * <p>Authenticated traffic is limited per user; anonymous traffic (login, registration) is
 * limited per client IP to blunt credential-stuffing. Every route selects its resolver
 * explicitly via SpEL ({@code #{@userKeyResolver}} / {@code #{@ipKeyResolver}}), but Spring
 * Cloud Gateway's autoconfigured {@code requestRateLimiterGatewayFilterFactory} also wants one
 * unqualified default bean — {@code ipKeyResolver} is marked {@link Primary} to satisfy that
 * without changing any route's actual resolver.
 */
@Configuration
public class RateLimiterConfig {

    /** Per-user limiting, falling back to IP when no identity is present. */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders()
                    .getFirst(JwtAuthenticationFilter.USER_ID_HEADER);
            return userId != null ? Mono.just(userId) : Mono.just(clientIp(exchange));
        };
    }

    /** Per-IP limiting for public endpoints; also the default for the autoconfigured filter. */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    private String clientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
}
