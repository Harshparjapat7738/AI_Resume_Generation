package ai.careerforge.profile.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.profile.api.dto.ProfileRequests.ExperienceRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.PersonalInformationRequest;
import ai.careerforge.profile.api.dto.ProfileResponses.EvidenceItemResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.ExperienceResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.PersonalInformationResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.ProfileResponse;
import ai.careerforge.profile.domain.Experience;
import ai.careerforge.profile.domain.PersonalInformation;
import ai.careerforge.profile.domain.Profile;
import ai.careerforge.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** See docs/API_CATALOG.md &sect;3 (Milestone 3 — profile-service). */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> get(@CallerId String userId) {
        return ResponseEntity.ok(toResponse(profileService.getOrCreate(userId)));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updatePersonalInformation(
            @CallerId String userId, @Valid @RequestBody PersonalInformationRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updatePersonalInformation(userId, request)));
    }

    @PostMapping("/experience")
    public ResponseEntity<ProfileResponse> addExperience(
            @CallerId String userId, @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addExperience(userId, request)));
    }

    @PutMapping("/experience/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateExperience(
            @CallerId String userId, @PathVariable String evidenceId,
            @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateExperience(userId, evidenceId, request)));
    }

    @DeleteMapping("/experience/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteExperience(
            @CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeExperience(userId, evidenceId)));
    }

    @GetMapping("/evidence")
    public ResponseEntity<List<EvidenceItemResponse>> evidence(@CallerId String userId) {
        Profile profile = profileService.getOrCreate(userId);
        List<EvidenceItemResponse> items = profile.experiences().stream()
                .map(ProfileController::toEvidenceItem)
                .toList();
        return ResponseEntity.ok(items);
    }

    private static EvidenceItemResponse toEvidenceItem(Experience e) {
        return new EvidenceItemResponse(
                e.evidenceId(), "EXPERIENCE",
                e.title() + (e.company() != null ? " at " + e.company() : ""),
                e.company(), e.description(), e.technologies(), e.metrics(), e.start(), e.end());
    }

    private static ProfileResponse toResponse(Profile profile) {
        PersonalInformation info = profile.personalInformation();
        PersonalInformationResponse infoResponse = new PersonalInformationResponse(
                info.fullName(), info.headline(), info.email(), info.phone(), info.links());
        List<ExperienceResponse> experiences = profile.experiences().stream()
                .map(e -> new ExperienceResponse(
                        e.evidenceId(), e.company(), e.title(), e.employmentType(), e.start(), e.end(),
                        e.current(), e.location(), e.bullets(), e.technologies(), e.metrics()))
                .toList();
        return new ProfileResponse(infoResponse, experiences);
    }
}
