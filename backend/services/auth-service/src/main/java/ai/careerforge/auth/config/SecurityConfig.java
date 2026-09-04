package ai.careerforge.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication is enforced once, at the gateway (docs/CODEBASE.md &sect;2). This service
 * is not reachable outside the internal network, so its own Spring Security filter chain
 * only needs to stay out of the way: no session, no CSRF token dance for a stateless JSON
 * API, no login form.
 *
 * <p>CSRF protection is intentionally out of scope for {@code /api/auth/refresh}: the
 * response is JSON read by fetch, and CORS on the gateway only allows the configured
 * frontend origin, so a cross-site form post cannot read the result. A future milestone may
 * add a double-submit token for defence in depth.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
