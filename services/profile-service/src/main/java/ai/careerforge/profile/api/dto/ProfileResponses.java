package ai.careerforge.profile.api.dto;

import java.util.List;

public final class ProfileResponses {

    private ProfileResponses() {
    }

    public record PersonalInformationResponse(
            String fullName, String headline, String email, String phone, List<String> links) {
    }

    public record ExperienceResponse(
            String evidenceId, String company, String title, String employmentType,
            String start, String end, boolean current, String location,
            List<String> bullets, List<String> technologies, List<String> metrics) {
    }

    public record EducationResponse(
            String evidenceId, String institution, String degree, String field,
            String start, String end, String grade, String description) {
    }

    public record SkillResponse(
            String evidenceId, String name, String category, String proficiency, Integer yearsOfExperience) {
    }

    public record ProjectResponse(
            String evidenceId, String name, String description, String role,
            List<String> technologies, List<String> metrics, String githubUrl, String liveUrl,
            String start, String end) {
    }

    public record CertificationResponse(
            String evidenceId, String name, String issuer, String issuedOn, String expiresOn,
            String credentialId, String credentialUrl) {
    }

    public record AchievementResponse(String evidenceId, String title, String description, String date) {
    }

    public record ProfileResponse(
            PersonalInformationResponse personalInformation,
            List<EducationResponse> education,
            List<ExperienceResponse> experiences,
            List<SkillResponse> skills,
            List<ProjectResponse> projects,
            List<CertificationResponse> certifications,
            List<AchievementResponse> achievements) {
    }

    /**
     * Shape must match {@code ai.careerforge.ai.api.dto.EvidenceItem} field-for-field — the
     * two services don't share a module (ADR-006), only a JSON contract. resume-service
     * fetches this list and forwards it verbatim into ai-service's evidence-selection and
     * resume-content requests. Every section — education, experience, skills, projects,
     * certifications, achievements — contributes evidence now, not just experience.
     */
    /**
     * @param description a single flattened blob (Experience's own {@code description()} joins
     *                     its bullets with spaces) — kept exactly as before for ai-service's
     *                     existing evidence-selection/JD-optimization prompting, which wants one
     *                     consolidated string per item, not a structured list.
     * @param bullets      the real, unflattened list backing {@code description} when the
     *                     evidence type actually has one (only Experience today — every other
     *                     type is empty here, same as before this field existed). Added so a
     *                     consumer that needs each bullet as its own line (render-service's
     *                     resume layout) doesn't have to re-split {@code description} by guessing
     *                     where one bullet ends and the next begins.
     */
    public record EvidenceItemResponse(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate,
            List<String> bullets) {
    }
}
