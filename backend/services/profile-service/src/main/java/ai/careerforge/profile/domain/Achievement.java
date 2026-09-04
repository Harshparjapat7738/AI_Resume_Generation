package ai.careerforge.profile.domain;

/** Embedded — docs/DATABASE.md &sect;3 shape. Awards, competitions, publications,
 *  leadership — anything that doesn't fit experience/education/projects. */
public class Achievement {

    private String evidenceId;
    private String title;
    private String description;
    private String date;

    protected Achievement() {
        // Spring Data
    }

    public Achievement(String evidenceId, String title, String description, String date) {
        this.evidenceId = evidenceId;
        this.title = title;
        this.description = description;
        this.date = date;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String date() {
        return date;
    }
}
