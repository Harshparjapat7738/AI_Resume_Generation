package ai.careerforge.profile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One per user (docs/DATABASE.md &sect;3): personal information plus six evidence-bearing
 * sections — education, experience, skills, projects, certifications, achievements. Every
 * item in every section carries an immutable {@code evidenceId} (prefix matches
 * ai-service's {@code EvidenceItem} pattern: EXP/EDU/SKILL/PROJ/CERT/ACH), assigned once via
 * {@link #nextEvidenceId(String)} and never reused, even after deletion.
 *
 * <p>Documents written before a given section existed simply have an empty list for it —
 * Spring Data leaves a missing field at its Java default, so existing profiles with only
 * personal info and experience keep working unchanged.
 */
@Document(collection = "profiles")
public class Profile {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("personalInformation")
    private PersonalInformation personalInformation;

    @Field("education")
    private List<Education> education = new ArrayList<>();

    @Field("experiences")
    private List<Experience> experiences = new ArrayList<>();

    @Field("skills")
    private List<Skill> skills = new ArrayList<>();

    @Field("projects")
    private List<Project> projects = new ArrayList<>();

    @Field("certifications")
    private List<Certification> certifications = new ArrayList<>();

    @Field("achievements")
    private List<Achievement> achievements = new ArrayList<>();

    /** Next unused sequence number per evidence-id prefix, e.g. {@code {"EXP": 3}}. */
    @Field("evidenceSequences")
    private Map<String, Integer> evidenceSequences = new HashMap<>();

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Version
    @Field("version")
    private Long version;

    @Field("schemaVersion")
    private int schemaVersion = 1;

    protected Profile() {
        // Spring Data
    }

    public Profile(String userId) {
        this.userId = userId;
        this.personalInformation = new PersonalInformation(null, null, null, null, List.of());
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public PersonalInformation personalInformation() {
        return personalInformation;
    }

    public void updatePersonalInformation(PersonalInformation info) {
        this.personalInformation = info;
    }

    /** Assigns and returns the next evidence ID for the given prefix (e.g. {@code "EXP"}). */
    public String nextEvidenceId(String prefix) {
        int next = evidenceSequences.merge(prefix, 1, Integer::sum);
        return prefix + "-" + String.format("%03d", next);
    }

    // ---- experience -----------------------------------------------------------

    public List<Experience> experiences() {
        return experiences;
    }

    public void addExperience(Experience experience) {
        experiences.add(experience);
    }

    public boolean removeExperience(String evidenceId) {
        return experiences.removeIf(e -> e.evidenceId().equals(evidenceId));
    }

    public void replaceExperience(String evidenceId, Experience updated) {
        replaceIn(experiences, evidenceId, Experience::evidenceId, updated);
    }

    // ---- education --------------------------------------------------------

    public List<Education> education() {
        return education;
    }

    public void addEducation(Education item) {
        education.add(item);
    }

    public boolean removeEducation(String evidenceId) {
        return education.removeIf(e -> e.evidenceId().equals(evidenceId));
    }

    public void replaceEducation(String evidenceId, Education updated) {
        replaceIn(education, evidenceId, Education::evidenceId, updated);
    }

    // ---- skills -------------------------------------------------------------

    public List<Skill> skills() {
        return skills;
    }

    public void addSkill(Skill item) {
        skills.add(item);
    }

    public boolean removeSkill(String evidenceId) {
        return skills.removeIf(s -> s.evidenceId().equals(evidenceId));
    }

    public void replaceSkill(String evidenceId, Skill updated) {
        replaceIn(skills, evidenceId, Skill::evidenceId, updated);
    }

    // ---- projects -----------------------------------------------------------

    public List<Project> projects() {
        return projects;
    }

    public void addProject(Project item) {
        projects.add(item);
    }

    public boolean removeProject(String evidenceId) {
        return projects.removeIf(p -> p.evidenceId().equals(evidenceId));
    }

    public void replaceProject(String evidenceId, Project updated) {
        replaceIn(projects, evidenceId, Project::evidenceId, updated);
    }

    // ---- certifications -------------------------------------------------

    public List<Certification> certifications() {
        return certifications;
    }

    public void addCertification(Certification item) {
        certifications.add(item);
    }

    public boolean removeCertification(String evidenceId) {
        return certifications.removeIf(c -> c.evidenceId().equals(evidenceId));
    }

    public void replaceCertification(String evidenceId, Certification updated) {
        replaceIn(certifications, evidenceId, Certification::evidenceId, updated);
    }

    // ---- achievements ---------------------------------------------------

    public List<Achievement> achievements() {
        return achievements;
    }

    public void addAchievement(Achievement item) {
        achievements.add(item);
    }

    public boolean removeAchievement(String evidenceId) {
        return achievements.removeIf(a -> a.evidenceId().equals(evidenceId));
    }

    public void replaceAchievement(String evidenceId, Achievement updated) {
        replaceIn(achievements, evidenceId, Achievement::evidenceId, updated);
    }

    private static <T> void replaceIn(List<T> list, String evidenceId,
                                      java.util.function.Function<T, String> idOf, T updated) {
        for (int i = 0; i < list.size(); i++) {
            if (idOf.apply(list.get(i)).equals(evidenceId)) {
                list.set(i, updated);
                return;
            }
        }
        throw new NoSuchElementException(evidenceId);
    }
}
