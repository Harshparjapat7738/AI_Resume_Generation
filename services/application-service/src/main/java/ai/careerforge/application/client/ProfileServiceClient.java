package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.ProfileDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "profile-service", configuration = FeignHeaderForwardingConfig.class)
public interface ProfileServiceClient {

    @GetMapping("/api/profile")
    ProfileDto getProfile();

    @GetMapping("/api/profile/evidence")
    List<EvidenceItem> getEvidence();
}
