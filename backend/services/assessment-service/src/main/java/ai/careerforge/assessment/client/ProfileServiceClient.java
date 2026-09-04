package ai.careerforge.assessment.client;

import ai.careerforge.assessment.client.ClientDtos.EvidenceItem;
import ai.careerforge.assessment.client.ClientDtos.ProfileDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "profile-service", configuration = FeignHeaderForwardingConfig.class)
public interface ProfileServiceClient {

    @GetMapping("/api/profile")
    ProfileDto getProfile();

    /** The same unified evidence endpoint application-service's {@code ResumeRenderService}
     *  reads (ADR-040) — used by {@code AtsScoringEngine} to score the content that would
     *  actually be rendered, independent of whether rendering itself succeeds. */
    @GetMapping("/api/profile/evidence")
    List<EvidenceItem> getEvidence();
}
