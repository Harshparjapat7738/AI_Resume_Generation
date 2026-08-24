package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.application.client.ClientDtos.JdOptimizationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "jd-service", configuration = FeignHeaderForwardingConfig.class)
public interface JdServiceClient {

    /** The JD's structured analysis (title, company, requirements, keywords) — computed now if
     *  this JD version has never been analysed yet, or read back from cache otherwise (ADR-037:
     *  no confirm gate blocks this any more). Used both to drive cover-letter evidence selection
     *  and, in {@code ApplicationService#create}, to guarantee {@code title}/{@code company} are
     *  populated before an {@link ai.careerforge.application.domain.Application} is created from
     *  them — those fields only exist on the raw JD document as a side effect of analysis having
     *  run at least once. */
    @GetMapping("/api/jd/{id}/analysis")
    JdAnalysisDto getAnalysis(@PathVariable("id") String id);

    /** The JD's current optimization (ADR-033) — used by {@code ResumeRenderService} (ADR-036)
     *  to learn which evidence ids are cited and how to rank them. {@code 404} if none has been
     *  computed for this job description yet. */
    @GetMapping("/api/jd/{id}/optimization")
    JdOptimizationDto getOptimization(@PathVariable("id") String id);
}
