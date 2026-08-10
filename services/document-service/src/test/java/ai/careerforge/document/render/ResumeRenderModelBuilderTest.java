package ai.careerforge.document.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.document.client.ClientDtos.EducationDto;
import ai.careerforge.document.client.ClientDtos.ExperienceDto;
import ai.careerforge.document.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ClientDtos.SkillDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeRenderModelBuilderTest {

    private final ResumeRenderModelBuilder builder = new ResumeRenderModelBuilder();

    @Test
    void mergesAiWrittenBulletsWithProfileFactsForExperienceTheAiSelected() {
        ProfileDto profile = profileWith(
                List.of(new ExperienceDto("EXP-001", "Northwind", "Backend Engineer", "FULL_TIME",
                        "2021-03", "2024-01", false, "Remote",
                        List.of("Profile's own raw bullet, unused when AI content exists"), List.of("Java"), List.of())),
                List.of(), List.of());

        ResumeVersionDto resume = resumeWith(Map.of(
                "summary", Map.of("text", "Grounded summary.", "evidenceIds", List.of("EXP-001")),
                "experienceBullets", List.of(Map.of(
                        "evidenceId", "EXP-001",
                        "bullets", List.of(Map.of("text", "AI-written, grounded bullet.", "evidenceIds", List.of("EXP-001")))))));

        ResumeRenderModel model = builder.build(resume, profile);

        assertThat(model.summary()).isEqualTo("Grounded summary.");
        assertThat(model.experience()).hasSize(1);
        ResumeRenderModel.ExperienceEntry entry = model.experience().get(0);
        assertThat(entry.company()).isEqualTo("Northwind");
        assertThat(entry.title()).isEqualTo("Backend Engineer");
        assertThat(entry.dateRange()).isEqualTo("2021-03 – 2024-01");
        assertThat(entry.bullets()).containsExactly("AI-written, grounded bullet.");
    }

    @Test
    void experienceNotSelectedByAiIsOmittedEvenThoughItExistsInProfile() {
        ProfileDto profile = profileWith(
                List.of(
                        new ExperienceDto("EXP-001", "Selected Co", "Engineer", null, "2020", "2021", false, null, List.of(), List.of(), List.of()),
                        new ExperienceDto("EXP-002", "Not Selected Co", "Engineer", null, "2019", "2020", false, null, List.of(), List.of(), List.of())),
                List.of(), List.of());

        ResumeVersionDto resume = resumeWith(Map.of(
                "experienceBullets", List.of(Map.of(
                        "evidenceId", "EXP-001",
                        "bullets", List.of(Map.of("text", "Only this one.", "evidenceIds", List.of("EXP-001")))))));

        ResumeRenderModel model = builder.build(resume, profile);

        assertThat(model.experience()).hasSize(1);
        assertThat(model.experience().get(0).company()).isEqualTo("Selected Co");
    }

    @Test
    void currentExperienceRendersPresentInsteadOfAnEndDate() {
        ProfileDto profile = profileWith(
                List.of(new ExperienceDto("EXP-001", "Co", "Engineer", null, "2022", null, true, null, List.of(), List.of(), List.of())),
                List.of(), List.of());
        ResumeVersionDto resume = resumeWith(Map.of(
                "experienceBullets", List.of(Map.of("evidenceId", "EXP-001", "bullets",
                        List.of(Map.of("text", "Still here.", "evidenceIds", List.of("EXP-001")))))));

        ResumeRenderModel model = builder.build(resume, profile);

        assertThat(model.experience().get(0).dateRange()).isEqualTo("2022 – Present");
    }

    @Test
    void skillsFollowAiOrderingWhenPresentButFallBackToProfileOrderWhenAbsent() {
        ProfileDto profileForOrdering = profileWith(List.of(), List.of(),
                List.of(new SkillDto("SKILL-001", "Java", null, null, null), new SkillDto("SKILL-002", "Kubernetes", null, null, null)));
        ResumeVersionDto resumeWithOrdering = resumeWith(Map.of("skillsOrdering", List.of("SKILL-002", "SKILL-001")));

        assertThat(builder.build(resumeWithOrdering, profileForOrdering).skills())
                .containsExactly("Kubernetes", "Java");

        ResumeVersionDto resumeWithoutOrdering = resumeWith(Map.of());
        assertThat(builder.build(resumeWithoutOrdering, profileForOrdering).skills())
                .containsExactly("Java", "Kubernetes");
    }

    @Test
    void educationCertificationsAndAchievementsAreFactualAndAlwaysShownAsIs() {
        ProfileDto profile = profileWith(List.of(),
                List.of(new EducationDto("EDU-001", "MIT", "BSc", "CS", "2015", "2019", null, null)),
                List.of());
        ResumeVersionDto resume = resumeWith(Map.of()); // AI content says nothing about education

        ResumeRenderModel model = builder.build(resume, profile);

        assertThat(model.education()).hasSize(1);
        assertThat(model.education().get(0).institution()).isEqualTo("MIT");
    }

    private static ProfileDto profileWith(
            List<ExperienceDto> experiences, List<EducationDto> education, List<SkillDto> skills) {
        return new ProfileDto(
                new PersonalInformationDto("Test User", null, null, null, List.of()),
                education, experiences, skills, List.of(), List.of(), List.of());
    }

    private static ResumeVersionDto resumeWith(Map<String, Object> content) {
        return new ResumeVersionDto(
                "resume-1", "jd-1", "Engineer", "Acme", "classic", "1",
                content, List.of(), List.of(), Map.of(), List.of(), null);
    }
}
