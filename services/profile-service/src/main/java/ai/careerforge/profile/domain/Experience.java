package ai.careerforge.profile.domain;

import java.util.List;

/**
 * One work-experience item. {@code evidenceId} is assigned once, on creation, and is
 * immutable — it is the anchor the AI cites and grounding validation resolves back to
 * (docs/DATABASE.md &sect;3).
 */
public class Experience {

    private String evidenceId;
    private String company;
    private String title;
    private String employmentType;
    private String start;
    private String end;
    private boolean current;
    private String location;
    private List<String> bullets = List.of();
    private List<String> technologies = List.of();
    private List<String> metrics = List.of();

    protected Experience() {
        // Spring Data
    }

    public Experience(String evidenceId, String company, String title, String employmentType,
                      String start, String end, boolean current, String location,
                      List<String> bullets, List<String> technologies, List<String> metrics) {
        this.evidenceId = evidenceId;
        this.company = company;
        this.title = title;
        this.employmentType = employmentType;
        this.start = start;
        this.end = end;
        this.current = current;
        this.location = location;
        this.bullets = bullets == null ? List.of() : List.copyOf(bullets);
        this.technologies = technologies == null ? List.of() : List.copyOf(technologies);
        this.metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String company() {
        return company;
    }

    public String title() {
        return title;
    }

    public String employmentType() {
        return employmentType;
    }

    public String start() {
        return start;
    }

    public String end() {
        return end;
    }

    public boolean current() {
        return current;
    }

    public String location() {
        return location;
    }

    public List<String> bullets() {
        return bullets;
    }

    public List<String> technologies() {
        return technologies;
    }

    public List<String> metrics() {
        return metrics;
    }

    /** Everything this bullet-free description can assert, joined for the evidence inventory. */
    public String description() {
        return String.join(" ", bullets);
    }
}
