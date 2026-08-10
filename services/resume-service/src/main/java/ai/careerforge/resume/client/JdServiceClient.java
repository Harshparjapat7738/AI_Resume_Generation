package ai.careerforge.resume.client;

import ai.careerforge.resume.client.ClientDtos.JdAnalysis;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "jd-service", configuration = FeignHeaderForwardingConfig.class)
public interface JdServiceClient {

    @GetMapping("/api/jd/{id}/analysis")
    JdAnalysis getAnalysis(@PathVariable("id") String jobDescriptionId);
}
