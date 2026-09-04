package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.domain.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/** Deterministic evidence pre-filtering (ADR-038) — the step that turns "send everything" into
 *  "send only what's plausibly relevant, plus a recency/credential anchor." */
class EvidenceMatcherTest {

    private final EvidenceMatcher matcher = new EvidenceMatcher();

    private EvidenceItem evidence(String id, String type, String title, String org, String description,
            List<String> technologies, String start, String end) {
        return new EvidenceItem(id, type, title, org, description, technologies, List.of(), start, end);
    }

    @Test
    void aRequirementWithNoLexicalOverlapAtAllBecomesAZeroCandidateGap() {
        Requirement kubernetes = new Requirement("REQ-001", "Production Kubernetes experience",
                "HARD_REQUIRED", 5, List.of("Kubernetes"));
        EvidenceItem unrelated = evidence("SKILL-001", "SKILL", "Watercolor painting", null,
                "Portrait painting", List.of(), null, null);

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(kubernetes), List.of(unrelated));

        assertThat(result.zeroCandidateRequirementIds()).containsExactly("REQ-001");
        assertThat(result.selectedEvidence()).isEmpty();
    }

    @Test
    void evidenceLexicallyOverlappingARequirementIsSelected() {
        Requirement java = new Requirement("REQ-001", "5 years of Java", "HARD_REQUIRED", 5, List.of("Java"));
        EvidenceItem relevant = evidence("EXP-001", "EXPERIENCE", "Backend Engineer", "Acme",
                "Built Java services", List.of("Java"), "2019", "Present");
        EvidenceItem irrelevant = evidence("SKILL-001", "SKILL", "Watercolor painting", null,
                "Portrait painting", List.of(), null, null);

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(java), List.of(relevant, irrelevant));

        assertThat(result.zeroCandidateRequirementIds()).isEmpty();
        assertThat(result.selectedEvidence()).extracting(EvidenceItem::evidenceId).contains("EXP-001");
        assertThat(result.candidatesByRequirement().get("REQ-001")).contains("EXP-001");
    }

    @Test
    @DisplayName("at most the top-K candidates per requirement are selected, not every overlapping item")
    void onlyTheTopKCandidatesPerRequirementAreSelected() {
        Requirement javaRequirement = new Requirement("REQ-001", "Java backend development", "HARD_REQUIRED", 5,
                List.of("Java"));
        List<EvidenceItem> tenJavaItems = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> evidence("EXP-0" + i, "EXPERIENCE", "Java Developer " + i, "Company " + i,
                        "Java backend development work", List.of("Java"), "2015", "2016"))
                .toList();

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(javaRequirement), tenJavaItems);

        assertThat(result.candidatesByRequirement().get("REQ-001")).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void theMostRecentExperienceIsKeptAsAnAnchorEvenWithNoLexicalOverlap() {
        Requirement kubernetes = new Requirement("REQ-001", "Production Kubernetes experience",
                "HARD_REQUIRED", 5, List.of("Kubernetes"));
        EvidenceItem currentRole = evidence("EXP-001", "EXPERIENCE", "Barista", "Coffee Shop",
                "Made coffee", List.of(), "2023", "Present");

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(kubernetes), List.of(currentRole));

        // Zero-candidate for REQ-001 (no lexical overlap), but still kept in the union as an anchor.
        assertThat(result.zeroCandidateRequirementIds()).containsExactly("REQ-001");
        assertThat(result.selectedEvidence()).extracting(EvidenceItem::evidenceId).contains("EXP-001");
    }

    @Test
    void certificationsAreKeptAsAnchorsRegardlessOfLexicalOverlap() {
        Requirement kubernetes = new Requirement("REQ-001", "Production Kubernetes experience",
                "HARD_REQUIRED", 5, List.of("Kubernetes"));
        EvidenceItem cert = evidence("CERT-001", "CERTIFICATION", "Certified Scrum Master", "Scrum.org",
                null, List.of(), "2020", null);

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(kubernetes), List.of(cert));

        assertThat(result.selectedEvidence()).extracting(EvidenceItem::evidenceId).contains("CERT-001");
    }

    @Test
    void emptyEvidenceMakesEveryRequirementAZeroCandidateGap() {
        Requirement java = new Requirement("REQ-001", "Java", "HARD_REQUIRED", 5, List.of("Java"));

        EvidenceMatcher.FilterResult result = matcher.filter(List.of(java), List.of());

        assertThat(result.zeroCandidateRequirementIds()).containsExactly("REQ-001");
        assertThat(result.selectedEvidence()).isEmpty();
    }
}
