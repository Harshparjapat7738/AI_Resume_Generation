package ai.careerforge.jd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * JD Service — accepts a job description as text, file or URL; fetches it through an SSRF-hardened client; extracts, normalises, analyses and requires explicit user confirmation.
 *
 * <p>Port {@code 8083}. Reachable only through the API Gateway; it is not published on a
 * public interface. See docs/CODEBASE.md for the full service contract.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class JdApplication {

    public static void main(String[] args) {
        SpringApplication.run(JdApplication.class, args);
    }
}
