package ai.careerforge.jd.service;

import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministically assembles the final optimization result (ADR-038) from three inputs that
 * each already exist for free: the JD analysis (requirements, their types/weights, and the flat
 * keyword vocabulary), the adjudication call's per-requirement verdicts, and the evidence
 * inventory. None of {@code keywords} classification, {@code missingRequirements} or
 * {@code emphasis} is a judgement call an LLM is needed for — a missing requirement is a set
 * difference, emphasis is a weighted sort, and a keyword's REQUIRED/PREFERRED priority reuses
 * the analysis's own requirement types. Computing them here is what let the adjudication call
 * (ai-service) shrink to matches-only.
 *
 * <p>The output shape is unchanged from before ADR-038 — {@code targetRole}, {@code
 * targetCompany}, {@code keywords[]}, {@code requirementMatches[]}, {@code missingRequirements[]},
 * {@code emphasis[]} — so nothing downstream (the persisted document shape, the frontend) needed
 * to change to consume it.
 */
@Component
public class OptimizationMerge {

    private static final int MAX_EMPHASIS = 10;
    private static final int MAX_KEYWORD_EVIDENCE_IDS = 5;

    /**
     * @param analysis                     the cached JD analysis (requirements, keywords)
     * @param adjudicationMatches          the validated {@code matches[]} array ai-service returned
     *                                     (empty/omitted if the adjudication call was skipped
     *                                     entirely because every requirement was zero-candidate)
     * @param zeroCandidateRequirementIds  requirements {@link EvidenceMatcher} never sent to Groq
     * @param fullEvidence                 the candidate's complete evidence inventory — used only
     *                                     for the (cheap, local, no-Groq-cost) keyword-to-evidence
     *                                     lexical mapping, never sent anywhere from here
     */
    public Map<String, Object> merge(JdAnalysis analysis, JsonNode adjudicationMatches,
            Set<String> zeroCandidateRequirementIds, List<EvidenceItem> fullEvidence) {

        Map<String, RequirementVerdict> verdictsByRequirement = new LinkedHashMap<>();
        if (adjudicationMatches != null) {
            for (JsonNode match : adjudicationMatches) {
                String requirementId = match.path("requirementId").asText(null);
                if (requirementId == null) {
                    continue;
                }
                List<String> evidenceIds = new ArrayList<>();
                match.path("evidenceIds").forEach(id -> evidenceIds.add(id.asText()));
                verdictsByRequirement.put(requirementId, new RequirementVerdict(
                        match.path("matchKind").asText("NONE"), evidenceIds));
            }
        }

        List<Map<String, Object>> requirementMatches = new ArrayList<>();
        List<Map<String, Object>> missingRequirements = new ArrayList<>();
        Map<String, Requirement> requirementsById = new LinkedHashMap<>();

        for (Requirement requirement : analysis.requirements()) {
            requirementsById.put(requirement.requirementId(), requirement);

            if (zeroCandidateRequirementIds.contains(requirement.requirementId())) {
                missingRequirements.add(missingEntry(requirement.requirementId(),
                        "No evidence in your profile matches this requirement yet."));
                continue;
            }
            RequirementVerdict verdict = verdictsByRequirement.get(requirement.requirementId());
            if (verdict == null) {
                // Had candidates, but the model didn't return a verdict for it — treat
                // conservatively as unsupported rather than silently dropping it.
                missingRequirements.add(missingEntry(requirement.requirementId(),
                        "No verified match could be confirmed for this requirement."));
                continue;
            }
            if ("NONE".equals(verdict.matchKind()) || verdict.evidenceIds().isEmpty()) {
                missingRequirements.add(missingEntry(requirement.requirementId(),
                        "The evidence considered did not actually demonstrate this requirement."));
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("requirementId", requirement.requirementId());
            entry.put("evidenceIds", verdict.evidenceIds());
            entry.put("matchStrength", verdict.matchKind());
            requirementMatches.add(entry);
        }

        Map<String, Object> optimisation = new LinkedHashMap<>();
        optimisation.put("targetRole", analysis.title());
        optimisation.put("targetCompany", analysis.company());
        optimisation.put("keywords", buildKeywords(analysis, fullEvidence));
        optimisation.put("requirementMatches", requirementMatches);
        optimisation.put("missingRequirements", missingRequirements);
        optimisation.put("emphasis", buildEmphasis(requirementMatches, requirementsById));
        return optimisation;
    }

    private Map<String, Object> missingEntry(String requirementId, String note) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("requirementId", requirementId);
        entry.put("note", note);
        return entry;
    }

    // ------------------------------------------------------------------ keywords ----

    /** Classifies each analysis keyword as REQUIRED/PREFERRED by whether it lexically overlaps
     *  a HARD_REQUIRED- vs PREFERRED-typed requirement, and maps it to evidence the same
     *  lexical way {@link EvidenceMatcher} ranks requirements — cheap, local, no Groq cost. */
    private List<Map<String, Object>> buildKeywords(JdAnalysis analysis, List<EvidenceItem> evidence) {
        List<Requirement> hardRequired = analysis.requirements().stream()
                .filter(r -> "HARD_REQUIRED".equals(r.type())).toList();
        List<Requirement> preferred = analysis.requirements().stream()
                .filter(r -> "PREFERRED".equals(r.type())).toList();

        List<Map<String, Object>> keywords = new ArrayList<>();
        for (String term : analysis.keywords()) {
            String priority = overlapsAny(term, hardRequired) ? "REQUIRED"
                    : "PREFERRED";
            List<String> evidenceIds = evidence.stream()
                    .filter(e -> lexicalOverlap(term, e.searchableText()))
                    .map(EvidenceItem::evidenceId)
                    .limit(MAX_KEYWORD_EVIDENCE_IDS)
                    .toList();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("term", term);
            entry.put("priority", priority);
            entry.put("category", null);
            entry.put("evidenceIds", evidenceIds);
            keywords.add(entry);
        }
        return keywords;
    }

    private boolean overlapsAny(String term, List<Requirement> requirements) {
        String needle = term.toLowerCase(java.util.Locale.ROOT);
        return requirements.stream().anyMatch(r ->
                (r.text() != null && r.text().toLowerCase(java.util.Locale.ROOT).contains(needle))
                        || r.normalisedTerms().stream()
                                .anyMatch(t -> t.equalsIgnoreCase(term)));
    }

    private boolean lexicalOverlap(String term, String searchableText) {
        return searchableText != null
                && searchableText.contains(term.toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------ emphasis ----

    /** Ranks the candidate's own evidence by how much of a STRONG/PARTIAL case it carries —
     *  weighted by the requirement's own analysis weight and the match strength — instead of
     *  asking the model to rank it, which is exactly a weighted sort over data already in hand. */
    private List<Map<String, Object>> buildEmphasis(List<Map<String, Object>> requirementMatches,
            Map<String, Requirement> requirementsById) {
        Map<String, Integer> scoreByEvidenceId = new LinkedHashMap<>();
        Map<String, Integer> matchCountByEvidenceId = new LinkedHashMap<>();

        for (Map<String, Object> match : requirementMatches) {
            String requirementId = (String) match.get("requirementId");
            Requirement requirement = requirementsById.get(requirementId);
            int weight = requirement != null ? requirement.weight() : 1;
            int bonus = "STRONG".equals(match.get("matchStrength")) ? 2 : 1;
            @SuppressWarnings("unchecked")
            List<String> evidenceIds = (List<String>) match.get("evidenceIds");
            for (String evidenceId : evidenceIds) {
                scoreByEvidenceId.merge(evidenceId, weight * bonus, Integer::sum);
                matchCountByEvidenceId.merge(evidenceId, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(scoreByEvidenceId.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        List<Map<String, Object>> emphasis = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : ranked) {
            if (rank > MAX_EMPHASIS) {
                break;
            }
            int matches = matchCountByEvidenceId.getOrDefault(entry.getKey(), 0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidenceId", entry.getKey());
            item.put("rank", rank);
            item.put("rationale", "Cited in " + matches + " requirement match" + (matches == 1 ? "" : "es")
                    + " for this role.");
            emphasis.add(item);
            rank++;
        }
        return emphasis;
    }

    private record RequirementVerdict(String matchKind, List<String> evidenceIds) {
    }

    /** Every evidence id the final result cites — recomputed from the assembled map so
     *  provenance always matches exactly what was persisted, never the wire response. */
    public static List<String> citedEvidenceIds(Map<String, Object> optimisation) {
        Set<String> ids = new LinkedHashSet<>();
        collectIds(optimisation.get("keywords"), ids);
        collectIds(optimisation.get("requirementMatches"), ids);
        Object emphasis = optimisation.get("emphasis");
        if (emphasis instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("evidenceId") instanceof String id) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    @SuppressWarnings("unchecked")
    private static void collectIds(Object arrayOfMaps, Set<String> ids) {
        if (!(arrayOfMaps instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("evidenceIds") instanceof List<?> evidenceIds) {
                for (Object id : evidenceIds) {
                    if (id instanceof String s) {
                        ids.add(s);
                    }
                }
            }
        }
    }
}
