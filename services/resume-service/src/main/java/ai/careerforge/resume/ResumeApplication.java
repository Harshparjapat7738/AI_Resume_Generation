package ai.careerforge.resume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Resume Service — orchestrates the generation workflow (evidence selection, AI call, grounding, rendering) and owns resume version history.
 *
 * <p>Port {@code 8084}. Reachable only through the API Gateway; it is not published on a
 * public interface. See docs/CODEBASE.md for the full service contract.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ResumeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeApplication.class, args);
    }
}
