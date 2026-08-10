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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Grounded application-email generation, following the same failure policy as
 * {@link ResumeContentService} and {@link CoverLetterContentService} (blueprint &sect;13):
 * generate, validate against the schema, then validate grounding; on failure regenerate once;
 * if it still fails, drop the offending paragraph(s) and report them rather than shipping an
 * unverified claim.
 *
 * <p>Simpler than cover-letter generation in two respects, matching an email's shorter form
 * (ARCHITECTURE_DECISIONS.md ADR-019): single-shot (no separate evidence-selection stage —
 * the model picks directly from the full inventory), and one body paragraph rather than a
 * paragraph array. Otherwise identical in spirit: the job title and company are real,
 * user-confirmed facts, passed to {@link GroundingValidator} as additional allowed context
 * rather than treated as unsupported entities (see that class's 3-arg {@code validate}
 * overload).
 */
@Service
public class EmailContentService {

    private static final Logger log = LoggerFactory.getLogger(EmailContentService.class);

    private static final String PROMPT = "email-content";
    private static final String SCHEMA = "email-content.schema.json";
    private static final int MAX_EVIDENCE_CHARS = 40_000;

    private final GroqClient groqClient;
    private final PromptRegistry promptRegistry;
    private final AiGenerationSupport support;
    private final GroundingValidator groundingValidator;
    private final ObjectMapper objectMapper;

    public EmailContentService(GroqClient groqClient, PromptRegistry promptRegistry,
                               AiGenerationSupport support,
                               GroundingValidator groundingValidator,
                               ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.promptRegistry = promptRegistry;
        this.support = support;
        this.groundingValidator = groundingValidator;
        this.objectMapper = objectMapper;
    }

    public AiResponses.EmailContentResponse generate(AiRequests.EmailContentRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());
        String userContent = buildUserContent(request);
        Set<String> allowedContext = request.company() == null
                ? Set.of(request.jobTitle())
                : Set.of(request.jobTitle(), request.company());

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

        return new AiResponses.EmailContentResponse(
                content, report, removed,
                new AiResponses.Provenance(prompt.versionLabel(), used.model(),
                        first.totalTokens() + (regenerated ? used.totalTokens() : 0),
                        regenerated));
    }

    // ------------------------------------------------------------------ prompt ----

    private String buildUserContent(AiRequests.EmailContentRequest request) {
        String jobContext = "Job title: " + UntrustedContent.sanitise(request.jobTitle()) + "\n"
                + "Company: " + UntrustedContent.sanitise(request.company());

        String evidence = request.evidence().stream()
                .map(this::renderEvidence)
                .collect(Collectors.joining("\n"));

        return UntrustedContent.fence("JOB_CONTEXT", jobContext, 4_000) + "\n\n"
                + UntrustedContent.fence("EVIDENCE", evidence, MAX_EVIDENCE_CHARS);
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

    /** Flattens the generated email into individually verifiable statements. */
    List<GeneratedStatement> extractStatements(JsonNode content) {
        List<GeneratedStatement> statements = new ArrayList<>();

        JsonNode body = content.path("bodyParagraph");
        if (body.isObject()) {
            statements.add(new GeneratedStatement("bodyParagraph",
                    body.path("text").asText(null), ids(body.path("evidenceIds"))));
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

    /** Drops a paragraph that failed verification twice, the same way
     *  {@code CoverLetterContentService} drops an unverifiable opening/closing paragraph. */
    JsonNode removeStatements(JsonNode content, List<String> locations) {
        ObjectNode root = content.deepCopy();
        if (locations.contains("bodyParagraph")) {
            root.remove("bodyParagraph");
        }
        if (locations.contains("closingParagraph")) {
            root.remove("closingParagraph");
        }
        return root;
    }
}
