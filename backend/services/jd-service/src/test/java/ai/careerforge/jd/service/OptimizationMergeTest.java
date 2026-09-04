package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Deterministic assembly of the final optimization result (ADR-038) — keywords classification,
 *  missing-requirement set difference, and emphasis ranking, none of which need a Groq call. */
class OptimizationMergeTest {

    private final OptimizationMerge merge = new OptimizationMerge();
    private final ObjectMapper mapper = new ObjectMapper();

    private JdAnalysis analysis(List<Requirement> requirements, List<String> keywords) {
        return new JdAnalysis("jd-1", "jdv-1", "Backend Engineer", "Acme", "Senior",
                keywords, requirements, "jd-analysis@v2", "openai/gpt-oss-120b");
    }

    @Test
    void aStrongMatchLandsInRequirementMatchesNotMissing() throws Exception {
        Requirement req = new Requirement("REQ-001", "5 years of Java", "HARD_REQUIRED", 5, List.of("Java"));
        var matches = mapper.readTree("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"}]}""")
                .path("matches");

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of()), matches, Set.of(), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requirementMatches = (List<Map<String, Object>>) result.get("requirementMatches");
        assertThat(requirementMatches).hasSize(1);
        assertThat(requirementMatches.get(0)).containsEntry("requirementId", "REQ-001")
                .containsEntry("matchStrength", "STRONG");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missing = (List<Map<String, Object>>) result.get("missingRequirements");
        assertThat(missing).isEmpty();
    }

    @Test
    void aZeroCandidateRequirementBecomesADeterministicGapWithoutEverBeingAdjudicated() {
        Requirement req = new Requirement("REQ-001", "Kubernetes", "HARD_REQUIRED", 5, List.of("Kubernetes"));

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of()),
                mapper.createArrayNode(), Set.of("REQ-001"), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missing = (List<Map<String, Object>>) result.get("missingRequirements");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0)).containsEntry("requirementId", "REQ-001");
    }

    @Test
    void aNoneVerdictAlsoBecomesMissingRatherThanAnEmptyMatch() throws Exception {
        Requirement req = new Requirement("REQ-001", "Kafka", "PREFERRED", 3, List.of("Kafka"));
        var matches = mapper.readTree("""
                {"matches":[{"requirementId":"REQ-001","evidenceIds":[],"matchKind":"NONE"}]}""").path("matches");

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of()), matches, Set.of(), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requirementMatches = (List<Map<String, Object>>) result.get("requirementMatches");
        assertThat(requirementMatches).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missing = (List<Map<String, Object>>) result.get("missingRequirements");
        assertThat(missing).extracting(m -> m.get("requirementId")).containsExactly("REQ-001");
    }

    @Test
    void aKeywordOverlappingAHardRequiredRequirementIsClassifiedRequired() {
        Requirement req = new Requirement("REQ-001", "5 years of Java", "HARD_REQUIRED", 5, List.of("Java"));
        EvidenceItem evidence = new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Acme",
                "Built Java services", List.of("Java"), List.of(), "2019", "Present");

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of("Java")),
                mapper.createArrayNode(), Set.of("REQ-001"), List.of(evidence));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) result.get("keywords");
        assertThat(keywords).hasSize(1);
        assertThat(keywords.get(0)).containsEntry("term", "Java").containsEntry("priority", "REQUIRED");
        @SuppressWarnings("unchecked")
        List<String> evidenceIds = (List<String>) keywords.get(0).get("evidenceIds");
        assertThat(evidenceIds).contains("EXP-004");
    }

    @Test
    void aKeywordWithNoEvidenceOverlapIsKeptWithAnEmptyEvidenceIdsGap() {
        Requirement req = new Requirement("REQ-001", "Kubernetes", "HARD_REQUIRED", 5, List.of("Kubernetes"));

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of("Kubernetes")),
                mapper.createArrayNode(), Set.of("REQ-001"), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) result.get("keywords");
        assertThat(keywords).hasSize(1);
        assertThat((List<?>) keywords.get(0).get("evidenceIds")).isEmpty();
    }

    @Test
    void emphasisRanksEvidenceByWeightedMatchStrengthNotArbitraryOrder() throws Exception {
        Requirement strongReq = new Requirement("REQ-001", "Java", "HARD_REQUIRED", 5, List.of("Java"));
        Requirement weakReq = new Requirement("REQ-002", "Kafka", "PREFERRED", 1, List.of("Kafka"));
        var matches = mapper.readTree("""
                {"matches":[
                  {"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"},
                  {"requirementId":"REQ-002","evidenceIds":["EXP-005"],"matchKind":"PARTIAL"}
                ]}""").path("matches");

        Map<String, Object> result = merge.merge(analysis(List.of(strongReq, weakReq), List.of()),
                matches, Set.of(), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emphasis = (List<Map<String, Object>>) result.get("emphasis");
        assertThat(emphasis).hasSize(2);
        assertThat(emphasis.get(0)).containsEntry("evidenceId", "EXP-004").containsEntry("rank", 1);
        assertThat(emphasis.get(1)).containsEntry("evidenceId", "EXP-005").containsEntry("rank", 2);
    }

    @Test
    void mergeIsDeterministic_sameInputTwiceProducesEqualOutput() throws Exception {
        Requirement strongReq = new Requirement("REQ-001", "Java", "HARD_REQUIRED", 5, List.of("Java"));
        Requirement weakReq = new Requirement("REQ-002", "Kafka", "PREFERRED", 1, List.of("Kafka"));
        var matches = mapper.readTree("""
                {"matches":[
                  {"requirementId":"REQ-001","evidenceIds":["EXP-004"],"matchKind":"STRONG"},
                  {"requirementId":"REQ-002","evidenceIds":["EXP-005"],"matchKind":"PARTIAL"}
                ]}""").path("matches");
        EvidenceItem e1 = new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Acme",
                "Built Java services", List.of("Java"), List.of(), "2019", "Present");
        EvidenceItem e2 = new EvidenceItem("EXP-005", "EXPERIENCE", "Kafka work", "Acme",
                "Kafka pipelines", List.of("Kafka"), List.of(), "2017", "2019");
        JdAnalysis analysis = analysis(List.of(strongReq, weakReq), List.of("Java", "Kafka"));

        Map<String, Object> first = merge.merge(analysis, matches, Set.of(), List.of(e1, e2));
        Map<String, Object> second = merge.merge(analysis, matches, Set.of(), List.of(e1, e2));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void targetRoleAndCompanyComeStraightFromAnalysisNotFromAnyLlmCall() {
        Requirement req = new Requirement("REQ-001", "Java", "HARD_REQUIRED", 5, List.of("Java"));

        Map<String, Object> result = merge.merge(analysis(List.of(req), List.of()),
                mapper.createArrayNode(), Set.of("REQ-001"), List.of());

        assertThat(result).containsEntry("targetRole", "Backend Engineer").containsEntry("targetCompany", "Acme");
    }
}
