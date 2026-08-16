package ai.careerforge.ai.api;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.client.GroqClient;
import ai.careerforge.ai.client.GroqException;
import ai.careerforge.ai.config.GroqProperties;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.service.EmailContentService;
import ai.careerforge.ai.service.EvidenceSelectionService;
import ai.careerforge.ai.service.JdAnalysisService;
import ai.careerforge.ai.service.JdOptimizationService;
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
 * drive Groq spend arbitrarily. Every legitimate call originates from jd-service,
 * jd-service or application-service, both on the
 * internal network.
 */
@RestController
@RequestMapping("/internal/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final JdAnalysisService jdAnalysisService;
    private final EvidenceSelectionService evidenceSelectionService;
    private final EmailContentService emailContentService;
    private final JdOptimizationService jdOptimizationService;
    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final PromptRegistry promptRegistry;

    public AiController(JdAnalysisService jdAnalysisService,
                        EvidenceSelectionService evidenceSelectionService,
                        EmailContentService emailContentService,
                        JdOptimizationService jdOptimizationService,
                        GroqClient groqClient,
                        GroqProperties groqProperties,
                        PromptRegistry promptRegistry) {
        this.jdAnalysisService = jdAnalysisService;
        this.evidenceSelectionService = evidenceSelectionService;
        this.emailContentService = emailContentService;
        this.jdOptimizationService = jdOptimizationService;
        this.groqClient = groqClient;
        this.groqProperties = groqProperties;
        this.promptRegistry = promptRegistry;
    }

    /**
     * JD optimization (ADR-033) — the operation that replaced resume/cover-letter content
     * generation. Returns targeting data (keywords, matches, gaps, emphasis), never prose.
     */
    @PostMapping("/jd-optimization")
    public ResponseEntity<AiResponses.JdOptimizationResponse> optimiseForJd(
            @Valid @RequestBody AiRequests.JdOptimizationRequest request) {
        return ResponseEntity.ok(call(() -> jdOptimizationService.optimise(request)));
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



    /** Grounded application-email content — single-shot, picks directly from the full
     *  evidence inventory (see ARCHITECTURE_DECISIONS.md ADR-019). */
    @PostMapping("/email-content")
    public ResponseEntity<AiResponses.EmailContentResponse> generateEmailContent(
            @Valid @RequestBody AiRequests.EmailContentRequest request) {
        return ResponseEntity.ok(call(() -> emailContentService.generate(request)));
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
     * Translates Groq transport failures into the platform error envelope. A rate limit
     * (429) is reported as such; anything else retryable becomes 502 so the caller can decide
     * to try again; the rest is a request-shaped problem the caller must fix.
     */
    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (GroqException ex) {
            if (ex.isRateLimited()) {
                // A quota problem, not a failed generation: 429 tells the caller (and the user)
                // that waiting is the fix, where a 502 implied something was broken.
                throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "The AI provider's rate limit was reached. Please wait a minute and retry.");
            }
            throw new ApiException(
                    ex.isRetryable() ? ErrorCode.AI_GENERATION_FAILED : ErrorCode.VALIDATION_ERROR,
                    ex.isRetryable()
                            ? ErrorCode.AI_GENERATION_FAILED.defaultMessage()
                            : "The generation request could not be processed.");
        }
    }
}
