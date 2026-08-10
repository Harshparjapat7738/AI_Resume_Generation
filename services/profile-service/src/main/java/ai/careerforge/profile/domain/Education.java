package ai.careerforge.profile.domain;

/** Embedded — docs/DATABASE.md &sect;3 shape, plus {@code description} (a small, additive
 *  extension beyond the documented fields, for context a bare degree/institution can't carry). */
public class Education {

    private String evidenceId;
    private String institution;
    private String degree;
    private String field;
    private String start;
    private String end;
    private String grade;
    private String description;

    protected Education() {
        // Spring Data
    }

    public Education(String evidenceId, String institution, String degree, String field,
                     String start, String end, String grade, String description) {
        this.evidenceId = evidenceId;
        this.institution = institution;
        this.degree = degree;
        this.field = field;
        this.start = start;
        this.end = end;
        this.grade = grade;
        this.description = description;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String institution() {
        return institution;
    }

    public String degree() {
        return degree;
    }

    public String field() {
        return field;
    }

    public String start() {
        return start;
    }

    public String end() {
        return end;
    }

    public String grade() {
        return grade;
    }

    public String description() {
        return description;
    }
}
