package ai.careerforge.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Application Service — cover letter and email assembly, Gmail draft creation, application history and status tracking.
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
