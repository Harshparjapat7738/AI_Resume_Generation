package ai.careerforge.resume.client;

import ai.careerforge.resume.client.ClientDtos.EvidenceSelectionRequest;
import ai.careerforge.resume.client.ClientDtos.EvidenceSelectionResponse;
import ai.careerforge.resume.client.ClientDtos.ResumeContentRequest;
import ai.careerforge.resume.client.ClientDtos.ResumeContentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal-only, not routed through the gateway (ADR-012) — reached via Eureka discovery. */
@FeignClient(name = "ai-service", path = "/internal/ai")
public interface AiServiceClient {

    @PostMapping("/evidence-selection")
    EvidenceSelectionResponse selectEvidence(@RequestBody EvidenceSelectionRequest request);

    @PostMapping("/resume-content")
    ResumeContentResponse generateContent(@RequestBody ResumeContentRequest request);
}
