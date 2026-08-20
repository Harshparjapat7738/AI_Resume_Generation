package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.application.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.application.client.ClientDtos.JobDescriptionDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "jd-service", configuration = FeignHeaderForwardingConfig.class)
public interface JdServiceClient {

    @GetMapping("/api/jd/{id}")
    JobDescriptionDto get(@PathVariable("id") String id);

    /** Requirements from a CONFIRMED job description — used to drive cover-letter evidence
     *  selection the same way resume-service uses it (mirrors {@code resume-service}'s
     *  {@code JdServiceClient}). {@code 409} if the JD isn't confirmed yet. */
    @GetMapping("/api/jd/{id}/analysis")
    JdAnalysisDto getAnalysis(@PathVariable("id") String id);

    /** The current JD optimization (ADR-033) — what {@code ResumeRenderService} draws on to
     *  decide which evidence belongs on a rendered resume. {@code 404} if none has been
     *  computed yet for this job description. */
    @GetMapping("/api/jd/{id}/optimization")
    JdOptimizationDto getOptimization(@PathVariable("id") String id);
}
