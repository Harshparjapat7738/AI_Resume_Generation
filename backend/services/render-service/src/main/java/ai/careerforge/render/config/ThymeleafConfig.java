package ai.careerforge.render.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * This service's own {@link TemplateEngine} — a manually built {@link SpringTemplateEngine}
 * instance, not the identically-typed bean {@code spring-boot-starter-thymeleaf}'s
 * autoconfiguration separately registers for Spring MVC view resolution (which this service
 * never uses: render-service resolves a {@code LoadedTemplate} through {@code TemplateProvider}
 * and feeds its content straight into this engine as a string — a {@link StringTemplateResolver}
 * treats the template argument as the template content itself, not a path to resolve — matching
 * ADR-036: rendering never touches a filesystem or classpath location outside
 * {@code TemplateProvider}).
 *
 * <p>{@code SpringTemplateEngine} specifically (not the base {@code TemplateEngine}) because it
 * uses Spring Expression Language for {@code ${...}} expressions instead of the default OGNL —
 * SpEL ships with Spring itself, so this needs no extra expression-language dependency beyond
 * what {@code spring-boot-starter-thymeleaf} already brings in.
 */
@Configuration
public class ThymeleafConfig {

    @Bean
    public TemplateEngine renderTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        // Every request already carries fully materialised content (TemplateProvider has no
        // notion of a cache key beyond the string itself) — caching identical strings buys
        // nothing here and only risks masking a template change during development.
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
