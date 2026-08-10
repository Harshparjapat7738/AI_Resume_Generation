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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Grounded cover-letter generation, following the same failure policy as
 * {@link ResumeContentService} (blueprint &sect;13): generate, validate against the schema,
 * then validate grounding; on failure regenerate once; if it still fails, drop the offending
 * paragraphs and report them rather than shipping an unverified claim.
 *
 * <p>Differs from resume generation in exactly one respect: the target job title and company
 * are real, user-confirmed facts that did not come from the candidate's own evidence, so they
 * are passed to {@link GroundingValidator} as additional allowed context rather than being
 * treated as unsupported entities (see that class's 3-arg {@code validate} overload).
 */
@Service
public class CoverLetterContentService {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterContentService.class);

    private static final String PROMPT = "cover-letter";
    private static final String SCHEMA = "cover-letter.schema.json";
    private static final int MAX_EVIDENCE_CHARS = 40_000;

    private static final Pattern BODY_PARAGRAPH_LOCATION =
            Pattern.compile("^bodyParagraphs\\[(\\d+)]$");

    private final GroqClient groqClient;
    private final PromptRegistry promptRegistry;
    private final AiGenerationSupport support;
    private final GroundingValidator groundingValidator;
    private final ObjectMapper objectMapper;

    public CoverLetterContentService(GroqClient groqClient, PromptRegistry promptRegistry,
                                     AiGenerationSupport support,
                                     GroundingValidator groundingValidator,
                                     ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.promptRegistry = promptRegistry;
        this.support = support;
        this.groundingValidator = groundingValidator;
        this.objectMapper = objectMapper;
    }

    public AiResponses.CoverLetterContentResponse generate(AiRequests.CoverLetterContentRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());
        String userContent = buildUserContent(request);
        Set<String> allowedContext = allowedContext(request);

        GroqClient.GroqResult first = groqClient.complete(prompt.body(), userContent, PROMPT);
        JsonNode content = support.validateSchema(first.content(), SCHEMA, PROMPT);
        GroundingReport report = groundingValidator.validate(
                extractStatements(content), request.evidence(), allowedContext);

        boolean regenerated = false;
        GroqClient.GroqResult used = first;

        if (!report.passed()) {
            log.warn("Grounding failed on first attempt, regenerating once: {}", report.summary());
            regenerated = true;

            GroqClient.GroqResult second = groqClient.complete(
                    prompt.body(), userContent + "\n\n" + correctionNotice(report), PROMPT);
            JsonNode retryContent = support.validateSchema(second.content(), SCHEMA, PROMPT);
            GroundingReport retryReport = groundingValidator.validate(
                    extractStatements(retryContent), request.evidence(), allowedContext);

            content = retryContent;
            report = retryReport;
            used = second;
        }

        List<String> removed = List.of();
        if (!report.passed()) {
            removed = report.failedLocations();
            log.warn("Grounding failed after regeneration; removing {} paragraph(s)", removed.size());
            content = removeStatements(content, removed);
            report = groundingValidator.validate(extractStatements(content), request.evidence(), allowedContext);
        }

        return new AiResponses.CoverLetterContentResponse(
                content, report, removed,
                new AiResponses.Provenance(prompt.versionLabel(), used.model(),
                        first.totalTokens() + (regenerated ? used.totalTokens() : 0),
                        regenerated));
    }

    // ------------------------------------------------------------------ prompt ----

    /** {@code jobTitle} and {@code company} are allowed as proper nouns even though they
     *  don't come from evidence; a blank/absent company is simply not added — see the
     *  prompt's instruction to address the letter generically when it's unknown. */
    private Set<String> allowedContext(AiRequests.CoverLetterContentRequest request) {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        if (request.jobTitle() != null && !request.jobTitle().isBlank()) {
            allowed.add(request.jobTitle());
        }
        if (request.company() != null && !request.company().isBlank()) {
            allowed.add(request.company());
        }
        return allowed;
    }

    private String buildUserContent(AiRequests.CoverLetterContentRequest request) {
        boolean hasCompany = request.company() != null && !request.company().isBlank();
        String jobContext = "Job title: " + UntrustedContent.sanitise(request.jobTitle()) + "\n"
                + "Company: " + (hasCompany ? UntrustedContent.sanitise(request.company()) : "(not stated — do not invent one)") + "\n"
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

    private String correctionNotice(GroundingReport report) {
        Map<?, Long> counts = report.countsByRule();
        return """
                -----CORRECTION-----
                The previous attempt was rejected by automated verification: %s.
                Regenerate using ONLY facts present in the EVIDENCE block, plus the job title
                and company named in JOB_CONTEXT. Do not state any number, date, employer,
                technology, certification or URL that does not appear there. If evidence is
                insufficient, write less.
                -----CORRECTION-----"""
                .formatted(counts);
    }

    // -------------------------------------------------------------- extraction ----

    /** Flattens the generated letter into individually verifiable statements. */
    List<GeneratedStatement> extractStatements(JsonNode content) {
        List<GeneratedStatement> statements = new ArrayList<>();

        JsonNode opening = content.path("openingParagraph");
        if (opening.isObject()) {
            statements.add(new GeneratedStatement("openingParagraph",
                    opening.path("text").asText(null), ids(opening.path("evidenceIds"))));
        }

        JsonNode body = content.path("bodyParagraphs");
        for (int i = 0; i < body.size(); i++) {
            JsonNode paragraph = body.get(i);
            statements.add(new GeneratedStatement("bodyParagraphs[" + i + "]",
                    paragraph.path("text").asText(null), ids(paragraph.path("evidenceIds"))));
        }

        JsonNode closing = content.path("closingParagraph");
        if (closing.isObject()) {
            statements.add(new GeneratedStatement("closingParagraph",
                    closing.path("text").asText(null), ids(closing.path("evidenceIds"))));
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
     * Deletes paragraphs that failed verification twice. {@code bodyParagraphs} entries are
     * removed from the highest index downwards so earlier indices stay valid while the list
     * shrinks; {@code openingParagraph}/{@code closingParagraph} are simply dropped, the same
     * way {@code ResumeContentService} drops an unverifiable summary.
     */
    JsonNode removeStatements(JsonNode content, List<String> locations) {
        ObjectNode root = content.deepCopy();

        if (locations.contains("openingParagraph")) {
            root.remove("openingParagraph");
        }
        if (locations.contains("closingParagraph")) {
            root.remove("closingParagraph");
        }

        List<Integer> bodyTargets = new ArrayList<>();
        for (String location : locations) {
            Matcher body = BODY_PARAGRAPH_LOCATION.matcher(location);
            if (body.matches()) {
                bodyTargets.add(Integer.parseInt(body.group(1)));
            }
        }
        bodyTargets.sort((a, b) -> Integer.compare(b, a));

        if (root.path("bodyParagraphs") instanceof ArrayNode paragraphs) {
            for (int index : bodyTargets) {
                if (index < paragraphs.size()) {
                    paragraphs.remove(index);
                }
            }
        }

        return root;
    }
}
