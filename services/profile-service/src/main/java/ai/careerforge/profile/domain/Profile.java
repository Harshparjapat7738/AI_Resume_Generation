package ai.careerforge.profile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One per user (docs/DATABASE.md &sect;3). Education, skills, certifications, projects and
 * achievements are documented there but not yet implemented in this milestone slice — only
 * {@code personalInformation} and {@code experiences} exist today; the same evidence-id
 * pattern extends to the rest without changing this shape.
 */
@Document(collection = "profiles")
public class Profile {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("personalInformation")
    private PersonalInformation personalInformation;

    @Field("experiences")
    private List<Experience> experiences = new ArrayList<>();

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

    public List<Experience> experiences() {
        return experiences;
    }

    /** Assigns and returns the next evidence ID for the given prefix (e.g. {@code "EXP"}). */
    public String nextEvidenceId(String prefix) {
        int next = evidenceSequences.merge(prefix, 1, Integer::sum);
        return prefix + "-" + String.format("%03d", next);
    }

    public void addExperience(Experience experience) {
        experiences.add(experience);
    }

    public boolean removeExperience(String evidenceId) {
        return experiences.removeIf(e -> e.evidenceId().equals(evidenceId));
    }

    public void replaceExperience(String evidenceId, Experience updated) {
        for (int i = 0; i < experiences.size(); i++) {
            if (experiences.get(i).evidenceId().equals(evidenceId)) {
                experiences.set(i, updated);
                return;
            }
        }
        throw new java.util.NoSuchElementException(evidenceId);
    }
}
