package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.AssessmentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "assessment-service", configuration = FeignHeaderForwardingConfig.class)
public interface AssessmentServiceClient {

    @GetMapping("/api/assessment/resume-versions/{resumeVersionId}")
    AssessmentDto get(@PathVariable("resumeVersionId") String resumeVersionId);
}
