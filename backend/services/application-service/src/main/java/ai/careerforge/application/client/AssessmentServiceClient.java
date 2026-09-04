package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.AssessmentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Path updated for ADR-040 ({@code AssessmentController}'s base mapping moved from the stale
 *  {@code /api/assessment/resume-versions} to {@code /api/assessment}). This lookup was already
 *  resume-version-keyed dead code with no live caller in the current resume-render flow (see
 *  CLAUDE.md "Known loose ends") — the rename keeps it textually consistent with the rest of
 *  assessment-service, it does not restore it to working order. */
@FeignClient(name = "assessment-service", configuration = FeignHeaderForwardingConfig.class)
public interface AssessmentServiceClient {

    @GetMapping("/api/assessment/{resumeVersionId}")
    AssessmentDto get(@PathVariable("resumeVersionId") String resumeVersionId);
}
