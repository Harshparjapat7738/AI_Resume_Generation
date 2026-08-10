package ai.careerforge.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication is enforced once, at the gateway (docs/CODEBASE.md &sect;2); this service
 * trusts the forwarded {@code X-User-Id} header the same way every other internal service
 * does (see {@code CallerIdArgumentResolver}). It would stay out of Spring Security's way
 * entirely except that {@code spring-boot-starter-oauth2-client} (added for the future Gmail
 * Google-OAuth flow, ADR pending) transitively pulls in Spring Security's autoconfiguration,
 * which otherwise defaults every endpoint to requiring a generated-password HTTP Basic login
 * — exactly the failure mode auth-service's own {@code SecurityConfig} documents. Mirrors
 * that class exactly.
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
