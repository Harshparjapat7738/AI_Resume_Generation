package ai.careerforge.profile.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.profile.api.dto.ProfileRequests.AchievementRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.CertificationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.EducationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.ExperienceRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.PersonalInformationRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.ProjectRequest;
import ai.careerforge.profile.api.dto.ProfileRequests.SkillRequest;
import ai.careerforge.profile.api.dto.ProfileResponses.AchievementResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.CertificationResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.EducationResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.EvidenceItemResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.ExperienceResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.PersonalInformationResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.ProfileResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.ProjectResponse;
import ai.careerforge.profile.api.dto.ProfileResponses.SkillResponse;
import ai.careerforge.profile.domain.Achievement;
import ai.careerforge.profile.domain.Certification;
import ai.careerforge.profile.domain.Education;
import ai.careerforge.profile.domain.Experience;
import ai.careerforge.profile.domain.PersonalInformation;
import ai.careerforge.profile.domain.Profile;
import ai.careerforge.profile.domain.Project;
import ai.careerforge.profile.domain.Skill;
import ai.careerforge.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Stream;
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

    // ---- experience -----------------------------------------------------------

    @PostMapping("/experience")
    public ResponseEntity<ProfileResponse> addExperience(
            @CallerId String userId, @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addExperience(userId, request)));
    }

    @PutMapping("/experience/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateExperience(
            @CallerId String userId, @PathVariable String evidenceId, @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateExperience(userId, evidenceId, request)));
    }

    @DeleteMapping("/experience/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteExperience(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeExperience(userId, evidenceId)));
    }

    // ---- education --------------------------------------------------------

    @PostMapping("/education")
    public ResponseEntity<ProfileResponse> addEducation(
            @CallerId String userId, @Valid @RequestBody EducationRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addEducation(userId, request)));
    }

    @PutMapping("/education/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateEducation(
            @CallerId String userId, @PathVariable String evidenceId, @Valid @RequestBody EducationRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateEducation(userId, evidenceId, request)));
    }

    @DeleteMapping("/education/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteEducation(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeEducation(userId, evidenceId)));
    }

    // ---- skills -------------------------------------------------------------

    @PostMapping("/skills")
    public ResponseEntity<ProfileResponse> addSkill(
            @CallerId String userId, @Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addSkill(userId, request)));
    }

    @PutMapping("/skills/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateSkill(
            @CallerId String userId, @PathVariable String evidenceId, @Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateSkill(userId, evidenceId, request)));
    }

    @DeleteMapping("/skills/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteSkill(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeSkill(userId, evidenceId)));
    }

    // ---- projects -----------------------------------------------------------

    @PostMapping("/projects")
    public ResponseEntity<ProfileResponse> addProject(
            @CallerId String userId, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addProject(userId, request)));
    }

    @PutMapping("/projects/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateProject(
            @CallerId String userId, @PathVariable String evidenceId, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateProject(userId, evidenceId, request)));
    }

    @DeleteMapping("/projects/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteProject(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeProject(userId, evidenceId)));
    }

    // ---- certifications -------------------------------------------------

    @PostMapping("/certifications")
    public ResponseEntity<ProfileResponse> addCertification(
            @CallerId String userId, @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addCertification(userId, request)));
    }

    @PutMapping("/certifications/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateCertification(
            @CallerId String userId, @PathVariable String evidenceId,
            @Valid @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateCertification(userId, evidenceId, request)));
    }

    @DeleteMapping("/certifications/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteCertification(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeCertification(userId, evidenceId)));
    }

    // ---- achievements ---------------------------------------------------

    @PostMapping("/achievements")
    public ResponseEntity<ProfileResponse> addAchievement(
            @CallerId String userId, @Valid @RequestBody AchievementRequest request) {
        return ResponseEntity.ok(toResponse(profileService.addAchievement(userId, request)));
    }

    @PutMapping("/achievements/{evidenceId}")
    public ResponseEntity<ProfileResponse> updateAchievement(
            @CallerId String userId, @PathVariable String evidenceId,
            @Valid @RequestBody AchievementRequest request) {
        return ResponseEntity.ok(toResponse(profileService.updateAchievement(userId, evidenceId, request)));
    }

    @DeleteMapping("/achievements/{evidenceId}")
    public ResponseEntity<ProfileResponse> deleteAchievement(@CallerId String userId, @PathVariable String evidenceId) {
        return ResponseEntity.ok(toResponse(profileService.removeAchievement(userId, evidenceId)));
    }

    // ---- evidence inventory ---------------------------------------------

    /** Every section's items, combined — this is the full inventory ai-service may cite from. */
    @GetMapping("/evidence")
    public ResponseEntity<List<EvidenceItemResponse>> evidence(@CallerId String userId) {
        Profile profile = profileService.getOrCreate(userId);
        List<EvidenceItemResponse> items = Stream.of(
                profile.experiences().stream().map(ProfileController::toEvidenceItem),
                profile.education().stream().map(ProfileController::toEvidenceItem),
                profile.skills().stream().map(ProfileController::toEvidenceItem),
                profile.projects().stream().map(ProfileController::toEvidenceItem),
                profile.certifications().stream().map(ProfileController::toEvidenceItem),
                profile.achievements().stream().map(ProfileController::toEvidenceItem))
                .flatMap(s -> s)
                .toList();
        return ResponseEntity.ok(items);
    }

    private static EvidenceItemResponse toEvidenceItem(Experience e) {
        return new EvidenceItemResponse(
                e.evidenceId(), "EXPERIENCE",
                e.title() + (e.company() != null ? " at " + e.company() : ""),
                e.company(), e.description(), e.technologies(), e.metrics(), e.start(), e.end(),
                e.bullets());
    }

    private static EvidenceItemResponse toEvidenceItem(Education e) {
        return new EvidenceItemResponse(
                e.evidenceId(), "EDUCATION",
                e.degree() + (e.field() != null ? " in " + e.field() : ""),
                e.institution(), e.description(), List.of(), List.of(), e.start(), e.end(), List.of());
    }

    private static EvidenceItemResponse toEvidenceItem(Skill s) {
        return new EvidenceItemResponse(
                s.evidenceId(), "SKILL", s.name(), null,
                s.proficiency(), List.of(s.name()), List.of(), null, null, List.of());
    }

    private static EvidenceItemResponse toEvidenceItem(Project p) {
        return new EvidenceItemResponse(
                p.evidenceId(), "PROJECT", p.name(), p.role(), p.description(),
                p.technologies(), p.metrics(), p.start(), p.end(), List.of());
    }

    private static EvidenceItemResponse toEvidenceItem(Certification c) {
        return new EvidenceItemResponse(
                c.evidenceId(), "CERTIFICATION", c.name(), c.issuer(), null,
                List.of(), List.of(), c.issuedOn(), c.expiresOn(), List.of());
    }

    private static EvidenceItemResponse toEvidenceItem(Achievement a) {
        return new EvidenceItemResponse(
                a.evidenceId(), "ACHIEVEMENT", a.title(), null, a.description(),
                List.of(), List.of(), a.date(), null, List.of());
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
        List<EducationResponse> education = profile.education().stream()
                .map(e -> new EducationResponse(
                        e.evidenceId(), e.institution(), e.degree(), e.field(), e.start(), e.end(),
                        e.grade(), e.description()))
                .toList();
        List<SkillResponse> skills = profile.skills().stream()
                .map(s -> new SkillResponse(s.evidenceId(), s.name(), s.category(), s.proficiency(), s.yearsOfExperience()))
                .toList();
        List<ProjectResponse> projects = profile.projects().stream()
                .map(p -> new ProjectResponse(p.evidenceId(), p.name(), p.description(), p.role(),
                        p.technologies(), p.metrics(), p.githubUrl(), p.liveUrl(), p.start(), p.end()))
                .toList();
        List<CertificationResponse> certifications = profile.certifications().stream()
                .map(c -> new CertificationResponse(c.evidenceId(), c.name(), c.issuer(), c.issuedOn(),
                        c.expiresOn(), c.credentialId(), c.credentialUrl()))
                .toList();
        List<AchievementResponse> achievements = profile.achievements().stream()
                .map(a -> new AchievementResponse(a.evidenceId(), a.title(), a.description(), a.date()))
                .toList();

        return new ProfileResponse(infoResponse, education, experiences, skills, projects, certifications, achievements);
    }
}
