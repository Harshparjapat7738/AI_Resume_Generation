package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.RenderResponse;
import ai.careerforge.application.client.ClientDtos.ResumeRenderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * render-service's rendering endpoint (ADR-036) — internal-only, resolved via Eureka like every
 * other client in this package (never a hardcoded host/port). {@code application-service} is
 * the only caller ADR-036 names for this endpoint; rendering logic itself stays entirely inside
 * render-service — this interface is a thin RPC boundary, not a place any resume-assembly or
 * PDF-generation code belongs.
 */
@FeignClient(name = "render-service", configuration = FeignHeaderForwardingConfig.class)
public interface RenderServiceClient {

    @PostMapping("/internal/render/resume")
    RenderResponse renderResume(@RequestBody ResumeRenderRequest request);
}
