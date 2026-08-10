package ai.careerforge.document.client;

import ai.careerforge.document.client.ClientDtos.ProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "profile-service", configuration = FeignHeaderForwardingConfig.class)
public interface ProfileServiceClient {

    @GetMapping("/api/profile")
    ProfileDto getProfile();
}
