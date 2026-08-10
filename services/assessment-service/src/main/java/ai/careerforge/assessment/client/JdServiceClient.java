package ai.careerforge.assessment.client;

import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "jd-service", configuration = FeignHeaderForwardingConfig.class)
public interface JdServiceClient {

    @GetMapping("/api/jd/{id}/analysis")
    JdAnalysisDto getAnalysis(@PathVariable("id") String jobDescriptionId);
}
