package ai.careerforge.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Application Service — owns the central {@code Application} aggregate: which job, what
 * generation type was requested, and references to the resume/cover-letter/email and
 * assessment that resulted (ARCHITECTURE_DECISIONS.md ADR-017). Cover letter and email
 * assembly and Gmail draft creation are represented in the domain but not yet implemented.
 *
 * <p>Port {@code 8088}. Reachable only through the API Gateway; it is not published on a
 * public interface. See docs/CODEBASE.md for the full service contract.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ApplicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationApplication.class, args);
    }
}
