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
 * Turns a confirmed JD plus the candidate's verified evidence into structured targeting data:
 * which of the posting's terms matter, which of them the candidate can actually back, which
 * requirements are unsupported, and what to lead with (ADR-033).
 *
 * <p><strong>This stage writes no candidate-facing prose.</strong> That is the whole point of
 * the product change it belongs to: CareerForge no longer generates a resume or cover letter, so
 * there is no generated sentence to ground. What it emits is selection, classification, ranking
 * and mapping over evidence the user already supplied — every candidate-facing fact in the
 * output is an {@code evidenceId} pointing back into their own profile.
 *
 * <p>Groq only, like every other JSON operation (ADR-032). Single-shot: unlike the content
 * stages it replaces, there is no regenerate-once loop, because there is no prose whose wording
 * could be corrected on a retry — an id the model invented is simply removed here and now, which
 * is deterministic and cheaper than another model call.
 *
 * <p><strong>Unknown ids are stripped, not trusted</strong> ({@link #stripUnknownIds}), the same
 * defence {@code EvidenceSelectionService} has always applied: a hallucinated {@code EXP-999}
 * must never reach persistence, a downstream consumer, or the prompt the user pastes into
 * another platform. A citation that survives is one the profile really contains.
 */
@Service
public class JdOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(JdOptimizationService.class);

    private static final String PROMPT = "jd-optimization";
    private static final String SCHEMA = "jd-optimization.schema.json";
    private static final int MAX_EVIDENCE_CHARS = 40_000;

    private final AiChatClient aiChatClient;
    private final AiGenerationSupport support;

    public JdOptimizationService(AiChatClient aiChatClient, AiGenerationSupport support) {
        this.aiChatClient = aiChatClient;
        this.support = support;
    }

    public AiResponses.JdOptimizationResponse optimise(AiRequests.JdOptimizationRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());
        String userContent = buildUserContent(request);

        AiChatClient.AiChatResult result = aiChatClient.complete(prompt.body(), userContent, PROMPT);
        JsonNode optimisation = support.validateSchema(result.content(), SCHEMA, PROMPT);

        Set<String> knownEvidenceIds = request.evidence().stream()
                .map(EvidenceItem::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> knownRequirementIds = request.requirements().stream()
                .map(AiRequests.RequirementInput::requirementId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        stripUnknownIds(optimisation, knownEvidenceIds, knownRequirementIds);

        return new AiResponses.JdOptimizationResponse(optimisation,
                new AiResponses.Provenance(prompt.versionLabel(), result.model(),
                        result.totalTokens(), false));
    }

    // ------------------------------------------------------------------ prompt ----

    private String buildUserContent(AiRequests.JdOptimizationRequest request) {
        String jobContext = "Role: " + UntrustedContent.sanitise(request.jobTitle()) + "\n"
                + "Company: " + (hasText(request.company())
                        ? UntrustedContent.sanitise(request.company()) : "(not stated — do not invent one)") + "\n"
                + "Seniority: " + UntrustedContent.sanitise(request.seniority()) + "\n"
                + "Requirements:\n"
                + request.requirements().stream()
                        .map(r -> "- [" + r.requirementId() + " | " + r.type() + " | weight " + r.weight() + "] "
                                + UntrustedContent.sanitise(r.text()))
                        .collect(Collectors.joining("\n"));

        String keywords = request.keywords() == null || request.keywords().isEmpty()
                ? "(none extracted)"
                : request.keywords().stream().map(UntrustedContent::sanitise).collect(Collectors.joining(", "));

        String evidence = request.evidence().stream()
                .map(item -> "[" + item.evidenceId() + "] " + item.type() + " — "
                        + UntrustedContent.sanitise(item.searchableText()))
                .collect(Collectors.joining("\n"));

        return UntrustedContent.fence("JOB_CONTEXT", jobContext, 30_000) + "\n\n"
                + UntrustedContent.fence("KEYWORDS", keywords, 8_000) + "\n\n"
                + UntrustedContent.fence("EVIDENCE", evidence, MAX_EVIDENCE_CHARS);
    }

    // ------------------------------------------------------------------ filter ----

    /**
     * Removes every citation the model invented, across all four sections.
     *
     * <p>An unknown <em>evidence</em> id is dropped from wherever it appears; a keyword or match
     * left with no evidence at all is kept, downgraded to the honest "unsupported" state
     * ({@code matchStrength: NONE}, empty {@code evidenceIds}) rather than deleted — the gap is
     * information the user needs, not noise. An entry keyed by an unknown <em>requirement</em>
     * id, by contrast, refers to a requirement that does not exist in this posting at all, so
     * there is nothing to report against and the whole entry goes.
     */
    private void stripUnknownIds(JsonNode optimisation, Set<String> knownEvidenceIds, Set<String> knownRequirementIds) {
        Set<String> removedEvidence = new HashSet<>();
        int removedEntries = 0;

        for (JsonNode keyword : optimisation.path("keywords")) {
            removedEvidence.addAll(pruneEvidenceIds(keyword.path("evidenceIds"), knownEvidenceIds));
        }

        removedEntries += pruneByRequirementId(optimisation.path("requirementMatches"), knownRequirementIds);
        for (JsonNode match : optimisation.path("requirementMatches")) {
            Set<String> dropped = pruneEvidenceIds(match.path("evidenceIds"), knownEvidenceIds);
            removedEvidence.addAll(dropped);
            // A match whose every citation was invented is not a match. Say so plainly rather
            // than leaving a STRONG verdict standing on nothing.
            if (match.path("evidenceIds").isEmpty() && match instanceof ObjectNode object) {
                object.put("matchStrength", "NONE");
            }
        }

        removedEntries += pruneByRequirementId(optimisation.path("missingRequirements"), knownRequirementIds);

        if (optimisation.path("emphasis") instanceof ArrayNode emphasis) {
            for (int i = emphasis.size() - 1; i >= 0; i--) {
                String evidenceId = emphasis.get(i).path("evidenceId").asText(null);
                if (evidenceId == null || !knownEvidenceIds.contains(evidenceId)) {
                    removedEvidence.add(String.valueOf(evidenceId));
                    emphasis.remove(i);
                    removedEntries++;
                }
            }
        }

        if (!removedEvidence.isEmpty() || removedEntries > 0) {
            log.warn("Stripped {} hallucinated evidence id(s) and {} entry/entries with unknown ids "
                    + "from JD optimisation output", removedEvidence.size(), removedEntries);
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

    /** Drops whole entries keyed by a requirement id this posting never had. */
    private int pruneByRequirementId(JsonNode arrayNode, Set<String> knownRequirementIds) {
        if (!(arrayNode instanceof ArrayNode entries)) {
            return 0;
        }
        int removed = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            String requirementId = entries.get(i).path("requirementId").asText(null);
            if (requirementId == null || !knownRequirementIds.contains(requirementId)) {
                entries.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** The evidence ids cited anywhere in a validated optimisation — what a caller persists as
     *  the provenance of this result, and what proves every candidate-facing fact traces back to
     *  the user's own profile. */
    public static List<String> citedEvidenceIds(JsonNode optimisation) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode keyword : optimisation.path("keywords")) {
            keyword.path("evidenceIds").forEach(id -> ids.add(id.asText()));
        }
        for (JsonNode match : optimisation.path("requirementMatches")) {
            match.path("evidenceIds").forEach(id -> ids.add(id.asText()));
        }
        for (JsonNode item : optimisation.path("emphasis")) {
            String id = item.path("evidenceId").asText(null);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }
}
