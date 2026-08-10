package ai.careerforge.profile.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.profile.api.dto.ProfileRequests.AchievementRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.CertificationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.EducationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.ExperienceRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.PersonalInformationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.ProjectRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.SkillRequest;
import ai.careerforge.profile.domain.Achievement;
import ai.careerforge.profile.domain.Certification;
import ai.careerforge.profile.domain.Education;
import ai.careerforge.profile.domain.Experience;
import ai.careerforge.profile.domain.PersonalInformation;
import ai.careerforge.profile.domain.Profile;
import ai.careerforge.profile.domain.Project;
import ai.careerforge.profile.domain.Skill;
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

    // ---- experience -----------------------------------------------------------

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
        requireOwned(profile.experiences().stream().anyMatch(e -> e.evidenceId().equals(evidenceId)));
        profile.replaceExperience(evidenceId, new Experience(
                evidenceId, request.company(), request.title(), request.employmentType(),
                request.start(), request.end(), request.current(), request.location(),
                request.bullets(), request.technologies(), request.metrics()));
        return profiles.save(profile);
    }

    public Profile removeExperience(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeExperience(evidenceId));
        return profiles.save(profile);
    }

    // ---- education --------------------------------------------------------

    public Profile addEducation(String userId, EducationRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("EDU");
        profile.addEducation(new Education(evidenceId, request.institution(), request.degree(),
                request.field(), request.start(), request.end(), request.grade(), request.description()));
        return profiles.save(profile);
    }

    public Profile updateEducation(String userId, String evidenceId, EducationRequest request) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.education().stream().anyMatch(e -> e.evidenceId().equals(evidenceId)));
        profile.replaceEducation(evidenceId, new Education(evidenceId, request.institution(), request.degree(),
                request.field(), request.start(), request.end(), request.grade(), request.description()));
        return profiles.save(profile);
    }

    public Profile removeEducation(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeEducation(evidenceId));
        return profiles.save(profile);
    }

    // ---- skills -------------------------------------------------------------

    public Profile addSkill(String userId, SkillRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("SKILL");
        profile.addSkill(new Skill(evidenceId, request.name(), request.category(),
                request.proficiency(), request.yearsOfExperience()));
        return profiles.save(profile);
    }

    public Profile updateSkill(String userId, String evidenceId, SkillRequest request) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.skills().stream().anyMatch(s -> s.evidenceId().equals(evidenceId)));
        profile.replaceSkill(evidenceId, new Skill(evidenceId, request.name(), request.category(),
                request.proficiency(), request.yearsOfExperience()));
        return profiles.save(profile);
    }

    public Profile removeSkill(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeSkill(evidenceId));
        return profiles.save(profile);
    }

    // ---- projects -----------------------------------------------------------

    public Profile addProject(String userId, ProjectRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("PROJ");
        profile.addProject(new Project(evidenceId, request.name(), request.description(), request.role(),
                request.technologies(), request.metrics(), request.githubUrl(), request.liveUrl(),
                request.start(), request.end()));
        return profiles.save(profile);
    }

    public Profile updateProject(String userId, String evidenceId, ProjectRequest request) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.projects().stream().anyMatch(p -> p.evidenceId().equals(evidenceId)));
        profile.replaceProject(evidenceId, new Project(evidenceId, request.name(), request.description(),
                request.role(), request.technologies(), request.metrics(), request.githubUrl(),
                request.liveUrl(), request.start(), request.end()));
        return profiles.save(profile);
    }

    public Profile removeProject(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeProject(evidenceId));
        return profiles.save(profile);
    }

    // ---- certifications -------------------------------------------------

    public Profile addCertification(String userId, CertificationRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("CERT");
        profile.addCertification(new Certification(evidenceId, request.name(), request.issuer(),
                request.issuedOn(), request.expiresOn(), request.credentialId(), request.credentialUrl()));
        return profiles.save(profile);
    }

    public Profile updateCertification(String userId, String evidenceId, CertificationRequest request) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.certifications().stream().anyMatch(c -> c.evidenceId().equals(evidenceId)));
        profile.replaceCertification(evidenceId, new Certification(evidenceId, request.name(), request.issuer(),
                request.issuedOn(), request.expiresOn(), request.credentialId(), request.credentialUrl()));
        return profiles.save(profile);
    }

    public Profile removeCertification(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeCertification(evidenceId));
        return profiles.save(profile);
    }

    // ---- achievements ---------------------------------------------------

    public Profile addAchievement(String userId, AchievementRequest request) {
        Profile profile = getOrCreate(userId);
        String evidenceId = profile.nextEvidenceId("ACH");
        profile.addAchievement(new Achievement(evidenceId, request.title(), request.description(), request.date()));
        return profiles.save(profile);
    }

    public Profile updateAchievement(String userId, String evidenceId, AchievementRequest request) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.achievements().stream().anyMatch(a -> a.evidenceId().equals(evidenceId)));
        profile.replaceAchievement(evidenceId,
                new Achievement(evidenceId, request.title(), request.description(), request.date()));
        return profiles.save(profile);
    }

    public Profile removeAchievement(String userId, String evidenceId) {
        Profile profile = getOrCreate(userId);
        requireOwned(profile.removeAchievement(evidenceId));
        return profiles.save(profile);
    }

    /** Loads the raw evidence inventory for a user — used internally by resume-service. */
    public Profile requireForEvidence(String userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "No profile evidence found. Add at least one profile entry before generating."));
    }

    /** Reported as 404 rather than 403 so item IDs cannot be enumerated (BOLA hardening). */
    private void requireOwned(boolean present) {
        if (!present) {
            throw ApiException.notOwned();
        }
    }
}
