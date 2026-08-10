package ai.careerforge.resume.client;

import ai.careerforge.resume.client.ClientDtos.EvidenceItem;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** Reached directly (not through the gateway) with the caller's identity forwarded manually — see {@link FeignHeaderForwardingConfig}. */
@FeignClient(name = "profile-service", configuration = FeignHeaderForwardingConfig.class)
public interface ProfileServiceClient {

    @GetMapping("/api/profile/evidence")
    List<EvidenceItem> getEvidence();
}
