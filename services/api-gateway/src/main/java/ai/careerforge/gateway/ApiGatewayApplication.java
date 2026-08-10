package ai.careerforge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Spring Cloud Gateway — the single public entry point for the CareerForge AI platform.
 *
 * <p>Responsibilities: routing to internal services, JWT verification, identity propagation,
 * Redis-backed rate limiting, CORS, and correlation-ID injection. It holds no business logic
 * and never talks to MongoDB.
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
