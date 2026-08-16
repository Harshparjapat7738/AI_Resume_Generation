package ai.careerforge.assessment.client;

import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "jd-service", configuration = FeignHeaderForwardingConfig.class)
public interface JdServiceClient {

    @GetMapping("/api/jd/{id}/analysis")
    JdAnalysisDto getAnalysis(@PathVariable("id") String jobDescriptionId);

    /** The persisted JD optimization this assessment scores (ADR-033). */
    @GetMapping("/api/jd/{id}/optimization")
    ClientDtos.JdOptimizationDto getOptimization(@PathVariable("id") String jobDescriptionId);
}
