package ai.careerforge.assessment.domain;

import java.util.List;

public class RequirementMatchResult {

    private String requirementId;
    private String text;
    private String type;
    private String matchStrength;
    private List<String> evidenceIds;

    protected RequirementMatchResult() {
        // Spring Data
    }

    public RequirementMatchResult(String requirementId, String text, String type, String matchStrength,
                                  List<String> evidenceIds) {
        this.requirementId = requirementId;
        this.text = text;
        this.type = type;
        this.matchStrength = matchStrength;
        this.evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
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

    public String matchStrength() {
        return matchStrength;
    }

    public List<String> evidenceIds() {
        return evidenceIds;
    }
}
