package ai.careerforge.jd.service;

import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.domain.Requirement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic, Groq-free evidence pre-filtering (ADR-038) — the step that turns "send the
 * candidate's entire evidence inventory" into "send only what could plausibly matter to this
 * job's requirements." This is what makes the adjudication call's input bounded regardless of
 * how large a profile grows, instead of relying solely on a blind character truncation.
 *
 * <p>Ranking is lexical (normalised-term token overlap) — there is no embedding model anywhere
 * in this codebase to rerank semantically, and adding one is out of scope for this change (it
 * would be new ML infrastructure, not a reuse of what exists). Lexical overlap plus a fixed
 * "anchor" set (most recent roles, any certifications) is a deliberately conservative trade:
 * it will occasionally miss a transferable-but-differently-worded match a semantic ranker would
 * catch, in exchange for zero added infrastructure and a fully explainable selection.
 */
@Component
public class EvidenceMatcher {

    /** Per-requirement candidate cap — 3-5 per ADR-038; 5 chosen so a requirement with several
     *  genuinely relevant items isn't arbitrarily starved. */
    private static final int TOP_K_PER_REQUIREMENT = 5;
    /** Global cap on how many distinct evidence units ever reach the adjudication call,
     *  regardless of how many requirements or how large the profile. */
    private static final int GLOBAL_MAX_UNITS = 40;
    /** Global cap on total searchable-text characters across the selected units — the second,
     *  independent backstop alongside the unit-count cap. */
    private static final int GLOBAL_MAX_CHARS = 6_000;
    /** Recency/anchor evidence kept regardless of lexical score, so filtering can never hide a
     *  candidate's most current or most credentialed evidence purely because its wording
     *  happens not to overlap this specific posting. */
    private static final int ANCHOR_MAX = 5;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9+.#]*");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "your", "you", "our", "are", "will", "have", "has",
            "this", "that", "from", "into", "who", "what", "how", "not", "can", "able", "years",
            "experience", "strong", "excellent", "good", "team", "work", "working", "role",
            "job", "using", "use", "of", "in", "to", "a", "an", "is", "on", "as", "or", "be");

    /**
     * @param selectedEvidence               union of every requirement's top-K candidates, plus
     *                                       the anchor set, deduplicated, capped globally
     * @param zeroCandidateRequirementIds    requirements with no evidence scoring above zero at
     *                                       all — these never reach Groq; they are deterministic
     *                                       gaps (ADR-038 §missingRequirements)
     * @param candidatesByRequirement        each requirement's own top-K candidate ids, ranked —
     *                                       used only by the deterministic skill-gap patch path
     *                                       to cite specific evidence for a newly-promoted
     *                                       requirement without a second Groq call
     */
    public record FilterResult(List<EvidenceItem> selectedEvidence, Set<String> zeroCandidateRequirementIds,
                               Map<String, List<String>> candidatesByRequirement) {
    }

    public FilterResult filter(List<Requirement> requirements, List<EvidenceItem> evidence) {
        if (evidence.isEmpty() || requirements.isEmpty()) {
            return new FilterResult(List.of(),
                    requirements.stream().map(Requirement::requirementId)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                    Map.of());
        }

        Map<String, List<String>> evidenceTokens = new LinkedHashMap<>();
        for (EvidenceItem item : evidence) {
            evidenceTokens.put(item.evidenceId(), tokenize(item.searchableText()));
        }

        Map<String, EvidenceItem> byId = new LinkedHashMap<>();
        for (EvidenceItem item : evidence) {
            byId.put(item.evidenceId(), item);
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        Set<String> zeroCandidateRequirementIds = new LinkedHashSet<>();
        Map<String, List<String>> candidatesByRequirement = new LinkedHashMap<>();

        for (Requirement requirement : requirements) {
            List<String> requirementTokens = new ArrayList<>(tokenize(requirement.text()));
            // normalisedTerms are the analysis stage's own distilled signal for this requirement
            // — weight them by including twice, so they dominate the overlap score over
            // incidental words in the full requirement sentence.
            requirement.normalisedTerms().forEach(term -> {
                requirementTokens.addAll(tokenize(term));
                requirementTokens.addAll(tokenize(term));
            });
            List<ScoredEvidence> scored = new ArrayList<>();
            for (EvidenceItem item : evidence) {
                int score = overlapScore(requirementTokens, evidenceTokens.get(item.evidenceId()));
                if (score > 0) {
                    scored.add(new ScoredEvidence(item.evidenceId(), score));
                }
            }
            if (scored.isEmpty()) {
                zeroCandidateRequirementIds.add(requirement.requirementId());
                continue;
            }
            scored.sort(Comparator.comparingInt(ScoredEvidence::score).reversed());
            List<String> topForRequirement = scored.stream()
                    .limit(TOP_K_PER_REQUIREMENT).map(ScoredEvidence::evidenceId).toList();
            topForRequirement.forEach(selectedIds::add);
            candidatesByRequirement.put(requirement.requirementId(), topForRequirement);
        }

        for (String anchorId : anchorSet(evidence)) {
            if (selectedIds.size() >= GLOBAL_MAX_UNITS) {
                break;
            }
            selectedIds.add(anchorId);
        }

        List<EvidenceItem> selected = applyGlobalCaps(selectedIds, byId);
        return new FilterResult(selected, zeroCandidateRequirementIds, candidatesByRequirement);
    }

    // ------------------------------------------------------------------ anchor set ----

    /** Always-kept evidence regardless of lexical relevance: the most recent experience(s) and
     *  every certification (capped) — a candidate's current role and credentials are exactly
     *  the evidence a purely lexical score is most likely to under-rank for a posting phrased
     *  differently from how the candidate described their own work. */
    private List<String> anchorSet(List<EvidenceItem> evidence) {
        List<EvidenceItem> experiences = evidence.stream()
                .filter(e -> "EXPERIENCE".equals(e.type()))
                .sorted(Comparator.comparing(EvidenceItem::endDate,
                        Comparator.nullsFirst(Comparator.reverseOrder())).reversed())
                .toList();
        List<EvidenceItem> certifications = evidence.stream()
                .filter(e -> "CERTIFICATION".equals(e.type()))
                .toList();

        List<String> anchors = new ArrayList<>();
        experiences.stream().limit(2).forEach(e -> anchors.add(e.evidenceId()));
        certifications.stream().limit(3).forEach(e -> anchors.add(e.evidenceId()));
        return anchors.stream().distinct().limit(ANCHOR_MAX).toList();
    }

    // ------------------------------------------------------------------ caps ----

    private List<EvidenceItem> applyGlobalCaps(Set<String> selectedIds, Map<String, EvidenceItem> byId) {
        List<EvidenceItem> ordered = selectedIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<EvidenceItem> capped = new ArrayList<>();
        int chars = 0;
        for (EvidenceItem item : ordered) {
            if (capped.size() >= GLOBAL_MAX_UNITS) {
                break;
            }
            int itemChars = item.searchableText().length();
            if (chars + itemChars > GLOBAL_MAX_CHARS && !capped.isEmpty()) {
                break;
            }
            capped.add(item);
            chars += itemChars;
        }
        return capped;
    }

    // ------------------------------------------------------------------ scoring ----

    private int overlapScore(List<String> requirementTokens, List<String> evidenceTokens) {
        if (requirementTokens.isEmpty() || evidenceTokens == null || evidenceTokens.isEmpty()) {
            return 0;
        }
        Set<String> evidenceSet = new HashSet<>(evidenceTokens);
        int score = 0;
        for (String token : requirementTokens) {
            if (evidenceSet.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private record ScoredEvidence(String evidenceId, int score) {
    }
}
