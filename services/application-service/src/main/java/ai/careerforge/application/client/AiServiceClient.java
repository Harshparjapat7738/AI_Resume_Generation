package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.CoverLetterContentRequest;
import ai.careerforge.application.client.ClientDtos.CoverLetterContentResponse;
import ai.careerforge.application.client.ClientDtos.EmailContentRequest;
import ai.careerforge.application.client.ClientDtos.EmailContentResponse;
import ai.careerforge.application.client.ClientDtos.EvidenceSelectionRequest;
import ai.careerforge.application.client.ClientDtos.EvidenceSelectionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal-only, not routed through the gateway (ADR-012) — reached via Eureka discovery. */
@FeignClient(name = "ai-service", path = "/internal/ai")
public interface AiServiceClient {

    @PostMapping("/email-content")
    EmailContentResponse generateEmailContent(@RequestBody EmailContentRequest request);

    /** Stage 1 of cover-letter generation: maps each requirement to the evidence that
     *  supports it — identical contract to resume-service's own call to this endpoint. */
    @PostMapping("/evidence-selection")
    EvidenceSelectionResponse selectEvidence(@RequestBody EvidenceSelectionRequest request);

    /** Stage 2: grounded cover-letter content, using the evidence ids selected in stage 1. */
    @PostMapping("/cover-letter")
    CoverLetterContentResponse generateCoverLetter(@RequestBody CoverLetterContentRequest request);
}
