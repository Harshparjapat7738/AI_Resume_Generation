package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.application.client.ClientDtos.TemplateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "resume-service", configuration = FeignHeaderForwardingConfig.class)
public interface ResumeServiceClient {

    @GetMapping("/api/resumes/{id}")
    ResumeVersionDto getResume(@PathVariable("id") String id);

    /** 404s for a missing, disabled, or unauthorized template — same ownership rule
     *  resume-service applies at generation time (ADR-016). */
    @GetMapping("/api/resumes/templates/{id}")
    TemplateDto getTemplate(@PathVariable("id") String id);
}
