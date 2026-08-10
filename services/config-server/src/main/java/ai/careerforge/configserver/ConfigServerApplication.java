package ai.careerforge.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server.
 *
 * <p>Serves per-service, per-profile configuration from the {@code native} backend rooted at
 * {@code infrastructure/config-repo}. Secrets are never stored in the config repo — they are
 * injected as environment variables and referenced with {@code ${ENV_VAR}} placeholders.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
