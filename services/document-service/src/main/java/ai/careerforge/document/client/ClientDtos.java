package ai.careerforge.document.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Local mirrors of the DTOs owned by resume-service and profile-service. Each service
 * defines its own client-side copy of the contracts it consumes — there is no shared DTO
 * module (ADR-006), only an agreed JSON shape. Mirrors assessment-service's identical
 * {@code client.ClientDtos} pattern.
 *
 * <p>All mirrors are {@code @JsonIgnoreProperties(ignoreUnknown = true)}: the owning
 * service is free to add fields without this client needing to change in lockstep.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ---- resume-service -------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GapDto(String requirementId, String text, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeVersionDto(
            String id, String jobDescriptionId, String jobTitle, String company,
            String templateId, String templateVersion,
            Map<String, Object> content, List<Map<String, Object>> evidenceMatches,
            List<GapDto> gaps, Map<String, Object> grounding, List<String> removedSections,
            Instant createdAt) {
    }

    // ---- profile-service --------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalInformationDto(String fullName, String headline, String email, String phone, List<String> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceDto(
            String evidenceId, String company, String title, String employmentType,
            String start, String end, boolean current, String location,
            List<String> bullets, List<String> technologies, List<String> metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EducationDto(
            String evidenceId, String institution, String degree, String field,
            String start, String end, String grade, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillDto(String evidenceId, String name, String category, String proficiency, Integer yearsOfExperience) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectDto(
            String evidenceId, String name, String description, String role,
            List<String> technologies, List<String> metrics, String githubUrl, String liveUrl,
            String start, String end) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CertificationDto(
            String evidenceId, String name, String issuer, String issuedOn, String expiresOn,
            String credentialId, String credentialUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AchievementDto(String evidenceId, String title, String description, String date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileDto(
            PersonalInformationDto personalInformation,
            List<EducationDto> education,
            List<ExperienceDto> experiences,
            List<SkillDto> skills,
            List<ProjectDto> projects,
            List<CertificationDto> certifications,
            List<AchievementDto> achievements) {
    }
}
