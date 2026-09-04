package ai.careerforge.profile.domain;

/**
 * Embedded — docs/DATABASE.md &sect;3 shape. {@code category} is free text, not an enum —
 * the frontend suggests common values (Programming Languages, Frameworks, Databases,
 * Cloud, DevOps, Tools) but nothing here enforces them.
 */
public class Skill {

    private String evidenceId;
    private String name;
    private String category;
    private String proficiency;
    private Integer yearsOfExperience;

    protected Skill() {
        // Spring Data
    }

    public Skill(String evidenceId, String name, String category, String proficiency, Integer yearsOfExperience) {
        this.evidenceId = evidenceId;
        this.name = name;
        this.category = category;
        this.proficiency = proficiency;
        this.yearsOfExperience = yearsOfExperience;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public String proficiency() {
        return proficiency;
    }

    public Integer yearsOfExperience() {
        return yearsOfExperience;
    }
}
