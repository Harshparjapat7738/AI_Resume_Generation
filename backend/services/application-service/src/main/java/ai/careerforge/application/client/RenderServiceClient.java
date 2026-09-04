package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.RenderResponse;
import ai.careerforge.application.client.ClientDtos.ResumeRenderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * render-service's internal rendering endpoint (ADR-036) — reached only over Eureka/Feign, the
 * same way {@code ai-service}'s internal endpoints are: render-service has no gateway route and
 * no public host port (see {@code docker-compose.yml}), so this Feign client is the only caller
 * that can ever reach it.
 */
@FeignClient(name = "render-service", configuration = FeignHeaderForwardingConfig.class)
public interface RenderServiceClient {

    @PostMapping("/internal/render/resume")
    RenderResponse renderResume(@RequestBody ResumeRenderRequest request);
}
