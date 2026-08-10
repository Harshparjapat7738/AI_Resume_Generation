package ai.careerforge.profile.domain;

import java.util.List;

/** Embedded — docs/DATABASE.md &sect;3 shape, plus {@code role}, {@code githubUrl},
 *  {@code liveUrl} (small, additive extensions the documented shape doesn't name but the
 *  product vision explicitly asks for). */
public class Project {

    private String evidenceId;
    private String name;
    private String description;
    private String role;
    private List<String> technologies = List.of();
    private List<String> metrics = List.of();
    private String githubUrl;
    private String liveUrl;
    private String start;
    private String end;

    protected Project() {
        // Spring Data
    }

    public Project(String evidenceId, String name, String description, String role,
                   List<String> technologies, List<String> metrics, String githubUrl, String liveUrl,
                   String start, String end) {
        this.evidenceId = evidenceId;
        this.name = name;
        this.description = description;
        this.role = role;
        this.technologies = technologies == null ? List.of() : List.copyOf(technologies);
        this.metrics = metrics == null ? List.of() : List.copyOf(metrics);
        this.githubUrl = githubUrl;
        this.liveUrl = liveUrl;
        this.start = start;
        this.end = end;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String role() {
        return role;
    }

    public List<String> technologies() {
        return technologies;
    }

    public List<String> metrics() {
        return metrics;
    }

    public String githubUrl() {
        return githubUrl;
    }

    public String liveUrl() {
        return liveUrl;
    }

    public String start() {
        return start;
    }

    public String end() {
        return end;
    }
}
