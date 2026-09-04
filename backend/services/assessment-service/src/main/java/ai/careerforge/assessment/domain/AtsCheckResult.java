package ai.careerforge.assessment.domain;

/**
 * One fractional ATS structural sub-check (ADR-008's formula, ADR-040's revival). {@code
 * passRatio} is always in {@code [0.0, 1.0]} — never a plain boolean, so a document that fails
 * one check inside one weighted category can still land just below a perfect score rather than
 * jumping straight to it (see ADR-008's own reasoning).
 */
public class AtsCheckResult {

    private String name;
    private String label;
    private double passRatio;
    private double weight;

    protected AtsCheckResult() {
        // Spring Data
    }

    public AtsCheckResult(String name, String label, double passRatio, double weight) {
        this.name = name;
        this.label = label;
        this.passRatio = passRatio;
        this.weight = weight;
    }

    public String name() {
        return name;
    }

    public String label() {
        return label;
    }

    public double passRatio() {
        return passRatio;
    }

    public double weight() {
        return weight;
    }
}
