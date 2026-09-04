package ai.careerforge.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Profile Service — the candidate's verified professional data and the ID-labelled evidence inventory that grounds every generated document.
 *
 * <p>Port {@code 8082}. Reachable only through the API Gateway; it is not published on a
 * public interface. See docs/CODEBASE.md for the full service contract.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ProfileApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileApplication.class, args);
    }
}
