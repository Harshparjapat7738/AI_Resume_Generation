package ai.careerforge.render;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Render Service — turns an already-grounded, schema-validated document model produced by
 * {@code ai-service} into a PDF (ADR-036): Thymeleaf template fill, then strict-XHTML
 * normalisation, then Open HTML to PDF (PDFBox). PDF-only — no DOCX, no mail-merge, no
 * custom-template support (that remains "My Templates", ADR-034, owned by profile-service).
 *
 * <p>Holds no AI credential and never calls {@code ai-service}: everything downstream of the
 * document model is pure, deterministic presentation of already-validated data, never a
 * decision about facts.
 *
 * <p>Port {@code 8084}. Internal-only, like every other business service — no gateway route
 * (ADR-036), called from {@code application-service} via Eureka/Feign.
 *
 * <p>This is the module skeleton only; rendering is implemented in a later step.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class RenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderApplication.class, args);
    }
}
