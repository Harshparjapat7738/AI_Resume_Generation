package ai.careerforge.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI Service — the only component that talks to Groq. Owns prompt versions, structured-output schemas and grounding validation. Holds no user database.
 *
 * <p>Port {@code 8085}. Reachable only through the API Gateway; it is not published on a
 * public interface. See docs/CODEBASE.md for the full service contract.
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
