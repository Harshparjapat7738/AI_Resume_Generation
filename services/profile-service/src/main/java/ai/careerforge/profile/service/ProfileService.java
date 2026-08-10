package ai.careerforge.profile.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.profile.api.dto.ProfileRequests.ExperienceRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.PersonalInformationRequest;
import ai.careerforge.profile.domain.Experience;
import ai.careerforge.profile.domain.PersonalInformation;
import ai.careerforge.profile.domain.Profile;
import ai.careerforge.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** Every user has exactly one profile; it is created lazily on first read or write. */
    public Profile getOrCreate(String userId) {
        return profiles.findByUserId(userId).orElseGet(() -> profiles.save(new Profile(userId)));
    }

    public Profile updatePersonalInformation(String userId, PersonalInformationRequest request) {
        Profile profile = getOrCreate(userId);
        profile.updatePersonalInformation(new PersonalInformation(
                request.fullName(), request.headline(), request.email(), request.phone(), request.links()));
        return profiles.save(profile);
    }

    public Profile addExperience(String userId, ExperienceRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("EXP");
        profile.addExperience(new Experience(
                evidenceId, request.company(), request.title(), request.employmentType(),
                request.start(), request.end(), request.current(), request.location(),
                request.bullets(), request.technologies(), request.metrics()));
        return profiles.save(profile);
    }

    public Profile updateExperience(String userId, String evidenceId, ExperienceRequest request) {
        Profile profile = getOrCreate(userId);
        boolean exists = profile.experiences().stream().anyMatch(e -> e.evidenceId().equals(evidenceId));
        if (!exists) {
            throw ApiException.notOwned();
        }
        profile.replaceExperience(evidenceId, new Experience(
                evidenceId, request.company(), request.title(), request.employmentType(),
                request.start(), request.end(), request.current(), request.location(),
                request.bullets(), request.technologies(), request.metrics()));
        return profiles.save(profile);
    }

    public Profile removeExperience(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        if (!profile.removeExperience(evidenceId)) {
            throw ApiException.notOwned();
        }
        return profiles.save(profile);
    }

    /** Loads the raw evidence inventory for a user — used internally by resume-service. */
    public Profile requireForEvidence(String userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "No profile evidence found. Add at least one experience before generating."));
    }
}
