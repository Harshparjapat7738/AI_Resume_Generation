package ai.careerforge.common.web;

import ai.careerforge.common.error.GlobalExceptionHandler;
import ai.careerforge.common.security.CallerIdArgumentResolver;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the shared web concerns in any service that puts platform-common on its
 * classpath, without requiring per-service component scanning of another package.
 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class PlatformWebAutoConfiguration {

    @Bean
    public WebMvcConfigurer callerIdWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerIdArgumentResolver());
            }
        };
    }
}
