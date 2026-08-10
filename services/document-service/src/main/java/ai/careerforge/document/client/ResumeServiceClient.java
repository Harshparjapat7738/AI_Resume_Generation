package ai.careerforge.document.client;

import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "resume-service", configuration = FeignHeaderForwardingConfig.class)
public interface ResumeServiceClient {

    @GetMapping("/api/resumes/{id}")
    ResumeVersionDto getResume(@PathVariable("id") String id);
}
