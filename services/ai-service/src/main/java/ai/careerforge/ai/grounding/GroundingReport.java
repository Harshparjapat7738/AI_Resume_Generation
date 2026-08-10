package ai.careerforge.ai.grounding;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The outcome of validating one generation.
 *
 * <p>Stored alongside every resume version so a document remains explainable long after it
 * was produced.
 *
 * @param passed             true only when there are no violations at all
 * @param violations         everything found, so the degrade path can drop exactly the
 *                           offending statements rather than the whole generation
 * @param checkedStatements  how many statements were examined
 */
public record GroundingReport(boolean passed, List<GroundingViolation> violations,
                              int checkedStatements) {

    public static GroundingReport of(List<GroundingViolation> violations, int checkedStatements) {
        return new GroundingReport(violations.isEmpty(), List.copyOf(violations), checkedStatements);
    }

    /** Locations that must be removed if a regeneration also fails. */
    public List<String> failedLocations() {
        return violations.stream().map(GroundingViolation::location).distinct().toList();
    }

    public Map<GroundingViolation.Rule, Long> countsByRule() {
        return violations.stream().collect(
                Collectors.groupingBy(GroundingViolation::rule, Collectors.counting()));
    }

    /** One-line summary safe for logs: rule names and counts, no generated content. */
    public String summary() {
        return passed
                ? "grounding passed (" + checkedStatements + " statements)"
                : "grounding failed: " + countsByRule();
    }
}
