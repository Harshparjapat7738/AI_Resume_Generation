package ai.careerforge.jd.client;

import ai.careerforge.jd.client.AiClientDtos.JdAnalysisRequest;
import ai.careerforge.jd.client.AiClientDtos.JdAnalysisResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ai-service is internal-only and not routed through the gateway (ADR-012); this call stays
 * entirely on the Docker/private network via Eureka service discovery.
 */
@FeignClient(name = "ai-service", path = "/internal/ai")
public interface AiServiceClient {

    @PostMapping("/jd-analysis")
    JdAnalysisResponse analyseJd(@RequestBody JdAnalysisRequest request);
}
