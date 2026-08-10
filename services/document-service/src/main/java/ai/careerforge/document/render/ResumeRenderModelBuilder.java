package ai.careerforge.document.render;

import ai.careerforge.document.client.ClientDtos.AchievementDto;
import ai.careerforge.document.client.ClientDtos.CertificationDto;
import ai.careerforge.document.client.ClientDtos.EducationDto;
import ai.careerforge.document.client.ClientDtos.ExperienceDto;
import ai.careerforge.document.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ProjectDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ClientDtos.SkillDto;
import ai.careerforge.document.render.ResumeRenderModel.AchievementEntry;
import ai.careerforge.document.render.ResumeRenderModel.CertificationEntry;
import ai.careerforge.document.render.ResumeRenderModel.EducationEntry;
import ai.careerforge.document.render.ResumeRenderModel.ExperienceEntry;
import ai.careerforge.document.render.ResumeRenderModel.ProjectEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Merges ai-service's grounded, JD-tailored content (which experiences/projects were
 * selected, and what to say about them) with profile-service's factual biographical data
 * (who, where, when) into one flat model a template can render without knowing anything
 * about either upstream shape. See {@link ResumeRenderModel} for the split rationale.
 */
@Component
public class ResumeRenderModelBuilder {

    public ResumeRenderModel build(ResumeVersionDto resume, ProfileDto profile) {
        PersonalInformationDto personal = profile.personalInformation();
        Map<String, ExperienceDto> experienceById = indexBy(profile.experiences(), ExperienceDto::evidenceId);
        Map<String, ProjectDto> projectById = indexBy(profile.projects(), ProjectDto::evidenceId);
        Map<String, SkillDto> skillById = indexBy(profile.skills(), SkillDto::evidenceId);

        Map<String, Object> content = resume.content() == null ? Map.of() : resume.content();

        return new ResumeRenderModel(
                personal == null ? null : personal.fullName(),
                personal == null ? null : personal.headline(),
                personal == null ? null : personal.email(),
                personal == null ? null : personal.phone(),
                personal == null || personal.links() == null ? List.of() : personal.links(),
                resume.jobTitle(),
                resume.company(),
                extractSummary(content),
                extractExperience(content, experienceById),
                extractEducation(profile.education()),
                extractSkills(content, skillById, profile.skills()),
                extractProjects(content, projectById),
                extractCertifications(profile.certifications()),
                extractAchievements(profile.achievements()));
    }

    // ---- summary ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String extractSummary(Map<String, Object> content) {
        Object summary = content.get("summary");
        if (summary instanceof Map<?, ?> map) {
            Object text = ((Map<String, Object>) map).get("text");
            return text == null ? null : text.toString();
        }
        return null;
    }

    // ---- experience (AI-selected group -> profile facts) -------------------------

    @SuppressWarnings("unchecked")
    private List<ExperienceEntry> extractExperience(Map<String, Object> content, Map<String, ExperienceDto> byId) {
        List<ExperienceEntry> entries = new ArrayList<>();
        Object groups = content.get("experienceBullets");
        if (!(groups instanceof List<?> groupList)) {
            return entries;
        }
        for (Object groupObj : groupList) {
            if (!(groupObj instanceof Map<?, ?> group)) continue;
            Map<String, Object> groupMap = (Map<String, Object>) group;
            String evidenceId = stringOf(groupMap.get("evidenceId"));
            ExperienceDto experience = byId.get(evidenceId);
            if (experience == null) {
                continue; // data drift (profile item since edited/removed) — skip defensively
            }
            List<String> bullets = new ArrayList<>();
            Object bulletsObj = groupMap.get("bullets");
            if (bulletsObj instanceof List<?> bulletList) {
                for (Object bulletObj : bulletList) {
                    if (bulletObj instanceof Map<?, ?> bullet) {
                        Object text = ((Map<String, Object>) bullet).get("text");
                        if (text != null) bullets.add(text.toString());
                    }
                }
            }
            if (bullets.isEmpty()) {
                bullets = experience.bullets() == null ? List.of() : experience.bullets();
            }
            entries.add(new ExperienceEntry(
                    experience.company(), experience.title(), experience.location(),
                    dateRange(experience.start(), experience.end(), experience.current()),
                    bullets, experience.technologies() == null ? List.of() : experience.technologies()));
        }
        return entries;
    }

    // ---- projects (AI-selected group -> profile facts) ----------------------------

    @SuppressWarnings("unchecked")
    private List<ProjectEntry> extractProjects(Map<String, Object> content, Map<String, ProjectDto> byId) {
        List<ProjectEntry> entries = new ArrayList<>();
        Object descriptions = content.get("projectDescriptions");
        if (!(descriptions instanceof List<?> list)) {
            return entries;
        }
        for (Object itemObj : list) {
            if (!(itemObj instanceof Map<?, ?> item)) continue;
            Map<String, Object> itemMap = (Map<String, Object>) item;
            String evidenceId = stringOf(itemMap.get("evidenceId"));
            ProjectDto project = byId.get(evidenceId);
            if (project == null) {
                continue;
            }
            Object text = itemMap.get("text");
            String description = text == null ? project.description() : text.toString();
            entries.add(new ProjectEntry(
                    project.name(), description, project.role(),
                    project.technologies() == null ? List.of() : project.technologies(),
                    project.githubUrl(), project.liveUrl(),
                    dateRange(project.start(), project.end(), false)));
        }
        return entries;
    }

    // ---- skills (AI ordering -> profile names, falling back to profile order) -----

    private List<String> extractSkills(Map<String, Object> content, Map<String, SkillDto> byId, List<SkillDto> allSkills) {
        Object orderingObj = content.get("skillsOrdering");
        if (orderingObj instanceof List<?> ordering && !ordering.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Object idObj : ordering) {
                SkillDto skill = byId.get(stringOf(idObj));
                if (skill != null) {
                    names.add(skill.name());
                }
            }
            if (!names.isEmpty()) {
                return names;
            }
        }
        return allSkills == null ? List.of() : allSkills.stream().map(SkillDto::name).toList();
    }

    // ---- education / certifications / achievements: factual, shown as-is ----------

    private List<EducationEntry> extractEducation(List<EducationDto> education) {
        if (education == null) return List.of();
        return education.stream()
                .map(e -> new EducationEntry(
                        e.institution(), e.degree(), e.field(), dateRange(e.start(), e.end(), false),
                        e.grade(), e.description()))
                .toList();
    }

    private List<CertificationEntry> extractCertifications(List<CertificationDto> certifications) {
        if (certifications == null) return List.of();
        return certifications.stream()
                .map(c -> new CertificationEntry(c.name(), c.issuer(), c.issuedOn(), c.expiresOn(), c.credentialId(), c.credentialUrl()))
                .toList();
    }

    private List<AchievementEntry> extractAchievements(List<AchievementDto> achievements) {
        if (achievements == null) return List.of();
        return achievements.stream()
                .map(a -> new AchievementEntry(a.title(), a.description(), a.date()))
                .toList();
    }

    // ---- helpers --------------------------------------------------------------

    private static <T> Map<String, T> indexBy(List<T> items, java.util.function.Function<T, String> idOf) {
        Map<String, T> map = new LinkedHashMap<>();
        if (items != null) {
            for (T item : items) {
                map.put(idOf.apply(item), item);
            }
        }
        return map;
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static String dateRange(String start, String end, boolean current) {
        String effectiveEnd = current ? "Present" : end;
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = effectiveEnd != null && !effectiveEnd.isBlank();
        if (hasStart && hasEnd) {
            return start + " – " + effectiveEnd;
        }
        if (hasStart) {
            return start;
        }
        if (hasEnd) {
            return effectiveEnd;
        }
        return "";
    }
}
