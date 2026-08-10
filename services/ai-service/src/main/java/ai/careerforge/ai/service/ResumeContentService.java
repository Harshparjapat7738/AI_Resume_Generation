package ai.careerforge.ai.service;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.client.GroqClient;
import ai.careerforge.ai.grounding.GroundingReport;
import ai.careerforge.ai.grounding.GroundingValidator;
import ai.careerforge.ai.grounding.GroundingValidator.GeneratedStatement;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.prompt.UntrustedContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 2 of the generation pipeline: turn selected evidence into resume content.
 *
 * <p>Implements the failure policy from blueprint §13 exactly:
 * <ol>
 *   <li>generate, validate against the schema, then validate grounding</li>
 *   <li>on failure, regenerate <strong>once</strong> and revalidate</li>
 *   <li>if it still fails, remove the unsupported statements and report them, so the user
 *       sees a gap rather than a fabrication</li>
 * </ol>
 *
 * <p>The service never returns unverified content. A statement either survived every check
 * or is not in the response.
 */
@Service
public class ResumeContentService {

    private static final Logger log = LoggerFactory.getLogger(ResumeContentService.class);

    private static final String PROMPT = "resume-content";
    private static final String SCHEMA = "resume-content.schema.json";
    private static final int MAX_EVIDENCE_CHARS = 40_000;

    private static final Pattern BULLET_LOCATION =
            Pattern.compile("^experienceBullets\\[(\\d+)]\\.bullets\\[(\\d+)]$");
    private static final Pattern PROJECT_LOCATION =
            Pattern.compile("^projectDescriptions\\[(\\d+)]$");

    private final GroqClient groqClient;
    private final PromptRegistry promptRegistry;
    private final AiGenerationSupport support;
    private final GroundingValidator groundingValidator;
    private final ObjectMapper objectMapper;

    public ResumeContentService(GroqClient groqClient, PromptRegistry promptRegistry,
                                AiGenerationSupport support,
                                GroundingValidator groundingValidator,
                                ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.promptRegistry = promptRegistry;
        this.support = support;
        this.groundingValidator = groundingValidator;
        this.objectMapper = objectMapper;
    }

    public AiResponses.ResumeContentResponse generate(AiRequests.ResumeContentRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());
        String userContent = buildUserContent(request);

        GroqClient.GroqResult first = groqClient.complete(prompt.body(), userContent, PROMPT);
        JsonNode content = support.validateSchema(first.content(), SCHEMA, PROMPT);
        GroundingReport report = groundingValidator.validate(
                extractStatements(content), request.evidence());

        boolean regenerated = false;
        GroqClient.GroqResult used = first;

        if (!report.passed()) {
            log.warn("Grounding failed on first attempt, regenerating once: {}", report.summary());
            regenerated = true;

            GroqClient.GroqResult second = groqClient.complete(
                    prompt.body(), userContent + "\n\n" + correctionNotice(report), PROMPT);
            JsonNode retryContent = support.validateSchema(second.content(), SCHEMA, PROMPT);
            GroundingReport retryReport = groundingValidator.validate(
                    extractStatements(retryContent), request.evidence());

            content = retryContent;
            report = retryReport;
            used = second;
        }

        List<String> removed = List.of();
        if (!report.passed()) {
            // Still failing. Drop the offending statements rather than shipping them.
            removed = report.failedLocations();
            log.warn("Grounding failed after regeneration; removing {} statement(s)",
                    removed.size());
            content = removeStatements(content, removed);
            report = groundingValidator.validate(extractStatements(content), request.evidence());
        }

        return new AiResponses.ResumeContentResponse(
                content, report, removed,
                new AiResponses.Provenance(prompt.versionLabel(), used.model(),
                        first.totalTokens() + (regenerated ? used.totalTokens() : 0),
                        regenerated));
    }

    // ------------------------------------------------------------------ prompt ----

    private String buildUserContent(AiRequests.ResumeContentRequest request) {
        String jobContext = "Job title: " + UntrustedContent.sanitise(request.jobTitle()) + "\n"
                + "Seniority: " + UntrustedContent.sanitise(request.seniority()) + "\n"
                + "Prioritised requirements:\n"
                + request.requirements().stream()
                        .map(r -> "- [" + r.type() + " w" + r.weight() + "] "
                                + UntrustedContent.sanitise(r.text()))
                        .collect(Collectors.joining("\n"));

        String evidence = request.evidence().stream()
                .map(this::renderEvidence)
                .collect(Collectors.joining("\n"));

        String selected = String.join(", ", request.selectedEvidenceIds());

        return UntrustedContent.fence("JOB_CONTEXT", jobContext, 20_000) + "\n\n"
                + UntrustedContent.fence("EVIDENCE", evidence, MAX_EVIDENCE_CHARS) + "\n\n"
                + UntrustedContent.fence("SELECTED", selected, 4_000);
    }

    private String renderEvidence(EvidenceItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(item.evidenceId()).append("] type=").append(item.type());
        appendField(sb, "title", item.title());
        appendField(sb, "organisation", item.organisation());
        appendField(sb, "dates", join(item.startDate(), item.endDate()));
        if (!item.technologies().isEmpty()) {
            appendField(sb, "technologies", String.join(", ", item.technologies()));
        }
        if (!item.metrics().isEmpty()) {
            appendField(sb, "metrics", String.join(" | ", item.metrics()));
        }
        appendField(sb, "detail", item.description());
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("\n  ").append(label).append(": ").append(UntrustedContent.sanitise(value));
        }
    }

    private String join(String start, String end) {
        if (start == null && end == null) {
            return null;
        }
        return (start == null ? "?" : start) + " to " + (end == null ? "?" : end);
    }

    /**
     * Tells the model what went wrong without quoting the offending text back at it —
     * echoing generated content into a follow-up prompt is how a bad claim gets reinforced.
     */
    private String correctionNotice(GroundingReport report) {
        Map<?, Long> counts = report.countsByRule();
        return """
                -----CORRECTION-----
                The previous attempt was rejected by automated verification: %s.
                Regenerate using ONLY facts present in the EVIDENCE block. Do not state any
                number, date, employer, technology, certification or URL that does not
                appear there. If evidence is insufficient, write less.
                -----CORRECTION-----"""
                .formatted(counts);
    }

    // -------------------------------------------------------------- extraction ----

    /** Flattens the generated document into individually verifiable statements. */
    List<GeneratedStatement> extractStatements(JsonNode content) {
        List<GeneratedStatement> statements = new ArrayList<>();

        JsonNode summary = content.path("summary");
        if (summary.isObject()) {
            statements.add(new GeneratedStatement("summary",
                    summary.path("text").asText(null), ids(summary.path("evidenceIds"))));
        }

        JsonNode experiences = content.path("experienceBullets");
        for (int i = 0; i < experiences.size(); i++) {
            JsonNode bullets = experiences.get(i).path("bullets");
            for (int j = 0; j < bullets.size(); j++) {
                JsonNode bullet = bullets.get(j);
                statements.add(new GeneratedStatement(
                        "experienceBullets[" + i + "].bullets[" + j + "]",
                        bullet.path("text").asText(null), ids(bullet.path("evidenceIds"))));
            }
        }

        JsonNode projects = content.path("projectDescriptions");
        for (int i = 0; i < projects.size(); i++) {
            JsonNode project = projects.get(i);
            statements.add(new GeneratedStatement("projectDescriptions[" + i + "]",
                    project.path("text").asText(null), ids(project.path("evidenceIds"))));
        }

        return statements;
    }

    private List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.asText()));
        return ids;
    }

    // ---------------------------------------------------------------- removal ----

    /**
     * Deletes statements that failed verification twice.
     *
     * <p>Removals are applied from the highest index downwards so earlier indices stay
     * valid while the list shrinks.
     */
    JsonNode removeStatements(JsonNode content, List<String> locations) {
        ObjectNode root = content.deepCopy();

        if (locations.contains("summary")) {
            root.remove("summary");
        }

        List<int[]> bulletTargets = new ArrayList<>();
        List<Integer> projectTargets = new ArrayList<>();

        for (String location : locations) {
            Matcher bullet = BULLET_LOCATION.matcher(location);
            if (bullet.matches()) {
                bulletTargets.add(new int[]{Integer.parseInt(bullet.group(1)),
                        Integer.parseInt(bullet.group(2))});
                continue;
            }
            Matcher project = PROJECT_LOCATION.matcher(location);
            if (project.matches()) {
                projectTargets.add(Integer.parseInt(project.group(1)));
            }
        }

        bulletTargets.sort((a, b) -> a[0] != b[0] ? Integer.compare(b[0], a[0])
                : Integer.compare(b[1], a[1]));
        for (int[] target : bulletTargets) {
            JsonNode group = root.path("experienceBullets").path(target[0]);
            if (group.path("bullets") instanceof ArrayNode bullets && target[1] < bullets.size()) {
                bullets.remove(target[1]);
            }
        }

        // An experience whose every bullet was removed contributes nothing; drop it so the
        // renderer does not emit an empty heading.
        if (root.path("experienceBullets") instanceof ArrayNode groups) {
            for (int i = groups.size() - 1; i >= 0; i--) {
                if (groups.get(i).path("bullets").isEmpty()) {
                    groups.remove(i);
                }
            }
        }

        projectTargets.sort((a, b) -> Integer.compare(b, a));
        if (root.path("projectDescriptions") instanceof ArrayNode projects) {
            for (int index : projectTargets) {
                if (index < projects.size()) {
                    projects.remove(index);
                }
            }
        }

        return root;
    }
}
