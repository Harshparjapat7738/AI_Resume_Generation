package ai.careerforge.jd.domain;

import java.util.List;

public class Requirement {

    private String requirementId;
    private String text;
    private String type;
    private int weight;
    private List<String> normalisedTerms = List.of();

    protected Requirement() {
        // Spring Data
    }

    public Requirement(String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
        this.requirementId = requirementId;
        this.text = text;
        this.type = type;
        this.weight = weight;
        this.normalisedTerms = normalisedTerms == null ? List.of() : List.copyOf(normalisedTerms);
    }

    public String requirementId() {
        return requirementId;
    }

    public String text() {
        return text;
    }

    public String type() {
        return type;
    }

    public int weight() {
        return weight;
    }

    public List<String> normalisedTerms() {
        return normalisedTerms;
    }
}
