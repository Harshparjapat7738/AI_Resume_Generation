package ai.careerforge.assessment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Local mirrors of the DTOs owned by resume-service, jd-service and profile-service. Each
 * service defines its own client-side copy of the contracts it consumes — there is no
 * shared DTO module (ADR-006), only an agreed JSON shape.
 *
 * <p>All mirrors are {@code @JsonIgnoreProperties(ignoreUnknown = true)}: the owning
 * service is free to add fields (e.g. profile-service adding education/skills/projects)
 * without this client needing to change in lockstep or breaking deserialization the moment
 * it doesn't.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ---- resume-service -----------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GapDto(String requirementId, String text, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeVersionDto(
            String id, String jobDescriptionId, String jobTitle, String company,
            Map<String, Object> content, List<Map<String, Object>> evidenceMatches,
            List<GapDto> gaps, Map<String, Object> grounding, List<String> removedSections,
            Instant createdAt) {
    }

    // ---- jd-service -----------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementDto(String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdAnalysisDto(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<RequirementDto> requirements) {
    }

    // ---- profile-service --------------------------------------------------

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
    public record ProfileDto(PersonalInformationDto personalInformation, List<ExperienceDto> experiences) {
    }
}
