package ai.careerforge.ai.service;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.client.GroqClient;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.prompt.UntrustedContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 1: decide which pieces of the candidate's evidence support each JD requirement.
 *
 * <p>Model output is filtered before it is returned: any cited evidence id that does not
 * exist in the supplied inventory is stripped. Grounding runs again on the generated text
 * in stage 2, but catching a hallucinated id here stops it propagating into a prompt.
 */
@Service
public class EvidenceSelectionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceSelectionService.class);

    private static final String PROMPT = "evidence-selection";
    private static final String SCHEMA = "evidence-selection.schema.json";

    private final GroqClient groqClient;
    private final AiGenerationSupport support;

    public EvidenceSelectionService(GroqClient groqClient, AiGenerationSupport support) {
        this.groqClient = groqClient;
        this.support = support;
    }

    public AiResponses.EvidenceSelectionResponse select(
            AiRequests.EvidenceSelectionRequest request) {

        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());

        String requirements = request.requirements().stream()
                .map(r -> "[" + r.requirementId() + "] (" + r.type() + ", weight " + r.weight()
                        + ") " + UntrustedContent.sanitise(r.text()))
                .collect(Collectors.joining("\n"));

        String evidence = request.evidence().stream()
                .map(item -> "[" + item.evidenceId() + "] " + item.type() + " — "
                        + UntrustedContent.sanitise(item.searchableText()))
                .collect(Collectors.joining("\n"));

        String userContent = UntrustedContent.fence("REQUIREMENTS", requirements, 30_000)
                + "\n\n" + UntrustedContent.fence("EVIDENCE", evidence, 40_000);

        GroqClient.GroqResult result = groqClient.complete(prompt.body(), userContent, PROMPT);
        JsonNode selection = support.validateSchema(result.content(), SCHEMA, PROMPT);

        stripUnknownEvidenceIds(selection, request.evidence().stream()
                .map(EvidenceItem::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new AiResponses.EvidenceSelectionResponse(selection,
                new AiResponses.Provenance(prompt.versionLabel(), result.model(),
                        result.totalTokens(), false));
    }

    /** Removes citations the model invented, and downgrades the match when none survive. */
    private void stripUnknownEvidenceIds(JsonNode selection, Set<String> knownIds) {
        Set<String> removed = new HashSet<>();

        for (JsonNode match : selection.path("matches")) {
            JsonNode idsNode = match.path("evidenceIds");
            if (!idsNode.isArray()) {
                continue;
            }
            for (int i = idsNode.size() - 1; i >= 0; i--) {
                String id = idsNode.get(i).asText();
                if (!knownIds.contains(id)) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) idsNode).remove(i);
                    removed.add(id);
                }
            }
            if (idsNode.isEmpty() && match.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) match)
                        .put("matchStrength", "NONE");
            }
        }

        if (!removed.isEmpty()) {
            log.warn("Stripped {} hallucinated evidence id(s) from selection output",
                    removed.size());
        }
    }
}
