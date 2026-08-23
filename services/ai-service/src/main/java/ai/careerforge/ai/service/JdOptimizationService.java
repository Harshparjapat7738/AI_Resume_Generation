package ai.careerforge.ai.service;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.client.AiChatClient;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.prompt.UntrustedContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Requirement adjudication (ADR-038, superseding the single-call design ADR-033 introduced):
 * given a job's requirements and the candidate's evidence — <strong>already pre-selected by the
 * caller for relevance to those specific requirements</strong>, never the caller's whole
 * inventory — decides, per requirement, whether the evidence actually demonstrates it.
 *
 * <p>This is deliberately the only judgement call this stage makes. Everything ADR-033's single
 * call used to also produce here — keyword prioritisation, missing-requirement reporting, and
 * emphasis ordering — is <strong>computed deterministically downstream</strong> (jd-service,
 * from this result plus the JD analysis already on hand) rather than asked of the model a second
 * time in a smaller call: none of those three are actually judgement calls (a missing
 * requirement is a set difference; emphasis is a weighted sort; keyword classification reuses
 * the analysis's own requirement types), so paying an LLM call for them was pure waste, not
 * decomposition. See ARCHITECTURE_DECISIONS.md ADR-038 for the full reasoning and the token-cost
 * comparison against the four-call decomposition that was considered and rejected.
 *
 * <p><strong>This stage writes no candidate-facing prose.</strong> What it emits is a verdict per
 * requirement, referencing only the evidence ids it was given — every candidate-facing fact in
 * the final, merged result is still an {@code evidenceId} pointing back into the profile.
 *
 * <p>Groq only, like every other JSON operation (ADR-032). Single-shot: there is no prose whose
 * wording could be corrected on a retry — an id the model invented is simply removed here and
 * now (see {@link #stripUnknownIds}), which is deterministic and cheaper than another model call.
 */
@Service
public class JdOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(JdOptimizationService.class);

    private static final String PROMPT = "jd-optimization";
    private static final String SCHEMA = "jd-optimization.schema.json";
    /** The caller is expected to have already filtered evidence down to a handful of candidates
     *  per requirement (jd-service's deterministic pre-filtering, ADR-038) — this is a ceiling
     *  against a caller bug, not the primary size control. */
    private static final int MAX_EVIDENCE_CHARS = 12_000;
    private static final int MAX_REQUIREMENTS_CHARS = 8_000;
    /** ADR-038: adjudication returns ids and a verdict only — nowhere near what the old
     *  four-field, free-text-bearing output needed. */
    private static final int MAX_COMPLETION_TOKENS = 1_000;

    private final AiChatClient aiChatClient;
    private final AiGenerationSupport support;

    public JdOptimizationService(AiChatClient aiChatClient, AiGenerationSupport support) {
        this.aiChatClient = aiChatClient;
        this.support = support;
    }

    public AiResponses.JdOptimizationResponse optimise(AiRequests.JdOptimizationRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());
        String userContent = buildUserContent(request);

        AiGenerationSupport.ValidatedCompletion completion = support.completeAndValidate(
                aiChatClient, prompt.body(), userContent, PROMPT, SCHEMA, MAX_COMPLETION_TOKENS);
        JsonNode adjudication = completion.content();

        Set<String> knownEvidenceIds = request.evidence().stream()
                .map(EvidenceItem::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> knownRequirementIds = request.requirements().stream()
                .map(AiRequests.RequirementInput::requirementId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        stripUnknownIds(adjudication, knownEvidenceIds, knownRequirementIds);

        return new AiResponses.JdOptimizationResponse(adjudication,
                new AiResponses.Provenance(prompt.versionLabel(), completion.result().model(),
                        completion.result().totalTokens(), completion.repaired()));
    }

    // ------------------------------------------------------------------ prompt ----

    private String buildUserContent(AiRequests.JdOptimizationRequest request) {
        String requirements = request.requirements().stream()
                .map(r -> "[" + r.requirementId() + " | " + r.type() + " | weight " + r.weight() + "] "
                        + UntrustedContent.sanitise(r.text()))
                .collect(Collectors.joining("\n"));

        String evidence = request.evidence().stream()
                .map(item -> "[" + item.evidenceId() + "] " + item.type() + " — "
                        + UntrustedContent.sanitise(item.searchableText()))
                .collect(Collectors.joining("\n"));

        return UntrustedContent.fence("REQUIREMENTS", requirements, MAX_REQUIREMENTS_CHARS) + "\n\n"
                + UntrustedContent.fence("EVIDENCE", evidence, MAX_EVIDENCE_CHARS);
    }

    // ------------------------------------------------------------------ filter ----

    /** Removes every citation the model invented, and drops a whole entry keyed by a requirement
     *  id that was never in the input. */
    private void stripUnknownIds(JsonNode adjudication, Set<String> knownEvidenceIds, Set<String> knownRequirementIds) {
        Set<String> removedEvidence = new HashSet<>();
        int removedEntries = 0;

        if (adjudication.path("matches") instanceof ArrayNode matches) {
            for (int i = matches.size() - 1; i >= 0; i--) {
                JsonNode match = matches.get(i);
                String requirementId = match.path("requirementId").asText(null);
                if (requirementId == null || !knownRequirementIds.contains(requirementId)) {
                    matches.remove(i);
                    removedEntries++;
                    continue;
                }
                Set<String> dropped = pruneEvidenceIds(match.path("evidenceIds"), knownEvidenceIds);
                removedEvidence.addAll(dropped);
                if (match.path("evidenceIds").isEmpty() && match instanceof ObjectNode object
                        && !"NONE".equals(match.path("matchKind").asText())) {
                    object.put("matchKind", "NONE");
                }
            }
        }

        if (!removedEvidence.isEmpty() || removedEntries > 0) {
            log.warn("Stripped {} hallucinated evidence id(s) and {} entry/entries with unknown ids "
                    + "from adjudication output", removedEvidence.size(), removedEntries);
        }
    }

    /** Drops unknown ids from an {@code evidenceIds} array in place; returns what was removed. */
    private Set<String> pruneEvidenceIds(JsonNode idsNode, Set<String> knownEvidenceIds) {
        Set<String> removed = new HashSet<>();
        if (!(idsNode instanceof ArrayNode ids)) {
            return removed;
        }
        for (int i = ids.size() - 1; i >= 0; i--) {
            String id = ids.get(i).asText();
            if (!knownEvidenceIds.contains(id)) {
                ids.remove(i);
                removed.add(id);
            }
        }
        return removed;
    }

    /** Every evidence id cited anywhere in a validated adjudication. */
    public static List<String> citedEvidenceIds(JsonNode adjudication) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode match : adjudication.path("matches")) {
            match.path("evidenceIds").forEach(id -> ids.add(id.asText()));
        }
        return new ArrayList<>(ids);
    }
}
