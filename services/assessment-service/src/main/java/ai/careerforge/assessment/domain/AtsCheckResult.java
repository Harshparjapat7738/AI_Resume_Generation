package ai.careerforge.assessment.domain;

/**
 * One weighted, deterministic check (docs/DATABASE.md &sect;3 shape). {@code passRatio} is
 * fractional (0.0-1.0), not boolean, so a check can be partially satisfied.
 */
public class AtsCheckResult {

    private String checkId;
    private String label;
    private double weight;
    private double passRatio;
    private String detail;
    private double earned;

    protected AtsCheckResult() {
        // Spring Data
    }

    public AtsCheckResult(String checkId, String label, double weight, double passRatio, String detail) {
        this.checkId = checkId;
        this.label = label;
        this.weight = weight;
        this.passRatio = Math.max(0.0, Math.min(1.0, passRatio));
        this.detail = detail;
        this.earned = this.weight * this.passRatio;
    }

    public String checkId() {
        return checkId;
    }

    public String label() {
        return label;
    }

    public double weight() {
        return weight;
    }

    public double passRatio() {
        return passRatio;
    }

    public String detail() {
        return detail;
    }

    public double earned() {
        return earned;
    }
}
