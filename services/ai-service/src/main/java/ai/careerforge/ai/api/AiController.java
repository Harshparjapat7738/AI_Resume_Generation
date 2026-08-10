package ai.careerforge.ai.api;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.client.GroqClient;
import ai.careerforge.ai.client.GroqException;
import ai.careerforge.ai.config.GroqProperties;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.service.EvidenceSelectionService;
import ai.careerforge.ai.service.JdAnalysisService;
import ai.careerforge.ai.service.ResumeContentService;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal AI endpoints.
 *
 * <p><strong>Not routed through the API Gateway (ADR-012).</strong> A browser-reachable AI
 * endpoint would let a caller bypass JD confirmation, evidence selection and grounding, and
 * drive Groq spend arbitrarily. Every legitimate call originates from jd-service or
 * resume-service on the internal network.
 */
@RestController
@RequestMapping("/internal/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final JdAnalysisService jdAnalysisService;
    private final EvidenceSelectionService evidenceSelectionService;
    private final ResumeContentService resumeContentService;
    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final PromptRegistry promptRegistry;

    public AiController(JdAnalysisService jdAnalysisService,
                        EvidenceSelectionService evidenceSelectionService,
                        ResumeContentService resumeContentService,
                        GroqClient groqClient,
                        GroqProperties groqProperties,
                        PromptRegistry promptRegistry) {
        this.jdAnalysisService = jdAnalysisService;
        this.evidenceSelectionService = evidenceSelectionService;
        this.resumeContentService = resumeContentService;
        this.groqClient = groqClient;
        this.groqProperties = groqProperties;
        this.promptRegistry = promptRegistry;
    }

    /** Extracts structured requirements from untrusted job-description text. */
    @PostMapping("/jd-analysis")
    public ResponseEntity<AiResponses.JdAnalysisResponse> analyseJd(
            @Valid @RequestBody AiRequests.JdAnalysisRequest request) {
        return ResponseEntity.ok(call(() -> jdAnalysisService.analyse(request)));
    }

    /** Stage 1: maps each requirement to the evidence that supports it. */
    @PostMapping("/evidence-selection")
    public ResponseEntity<AiResponses.EvidenceSelectionResponse> selectEvidence(
            @Valid @RequestBody AiRequests.EvidenceSelectionRequest request) {
        return ResponseEntity.ok(call(() -> evidenceSelectionService.select(request)));
    }

    /** Stage 2: writes resume content, then verifies every statement against the evidence. */
    @PostMapping("/resume-content")
    public ResponseEntity<AiResponses.ResumeContentResponse> generateResumeContent(
            @Valid @RequestBody AiRequests.ResumeContentRequest request) {
        return ResponseEntity.ok(call(() -> resumeContentService.generate(request)));
    }

    /**
     * Diagnostic: is Groq configured and reachable?
     *
     * <p>Useful when bringing the stack up, because a missing or wrong API key otherwise
     * only shows up on a user's first generation. Reports the key in masked form only.
     */
    @GetMapping("/status")
    public ResponseEntity<AiResponses.StatusResponse> status() {
        boolean reachable;
        String detail;
        try {
            groqClient.complete(
                    "Reply with the JSON object {\"ok\":true} and nothing else.",
                    "ping", "status");
            reachable = true;
            detail = "Groq responded successfully.";
        } catch (GroqException ex) {
            reachable = false;
            detail = ex.getMessage();
            log.warn("Groq connectivity check failed: {}", ex.getMessage());
        }

        return ResponseEntity.ok(new AiResponses.StatusResponse(
                groqProperties.apiKey() != null && !groqProperties.apiKey().isBlank(),
                groqProperties.maskedKey(),
                groqProperties.model(),
                groqProperties.baseUrl(),
                promptRegistry.latestVersions(),
                reachable,
                detail));
    }

    /**
     * Translates Groq transport failures into the platform error envelope. Retryable
     * failures become 502 so the caller can decide to try again; anything else is a
     * request-shaped problem the caller must fix.
     */
    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (GroqException ex) {
            throw new ApiException(
                    ex.isRetryable() ? ErrorCode.AI_GENERATION_FAILED : ErrorCode.VALIDATION_ERROR,
                    ex.isRetryable()
                            ? ErrorCode.AI_GENERATION_FAILED.defaultMessage()
                            : "The generation request could not be processed.");
        }
    }
}
