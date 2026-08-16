package ai.careerforge.jd.client;

import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The candidate's verified evidence inventory — the only source of candidate facts JD
 * optimization has (ADR-033). Reached directly (not through the gateway) with the caller's
 * identity forwarded manually — see {@link FeignHeaderForwardingConfig}.
 */
@FeignClient(name = "profile-service", configuration = FeignHeaderForwardingConfig.class)
public interface ProfileServiceClient {

    @GetMapping("/api/profile/evidence")
    List<EvidenceItem> getEvidence();
}
