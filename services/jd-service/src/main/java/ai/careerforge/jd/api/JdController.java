package ai.careerforge.jd.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.jd.api.dto.JdRequests.EditJdRequest;
import ai.careerforge.jd.api.dto.JdRequests.FetchUrlRequest;
import ai.careerforge.jd.api.dto.JdRequests.SubmitJdRequest;
import ai.careerforge.jd.api.dto.JdResponses.JdAnalysisResponse;
import ai.careerforge.jd.api.dto.JdResponses.JdDetailResponse;
import ai.careerforge.jd.api.dto.JdResponses.JdOptimizationResponse;
import ai.careerforge.jd.api.dto.JdResponses.JdSummaryResponse;
import ai.careerforge.jd.api.dto.JdResponses.PageResponse;
import ai.careerforge.jd.api.dto.JdResponses.RequirementResponse;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.JdVersion;
import ai.careerforge.jd.domain.JobDescription;
import ai.careerforge.jd.service.JdService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** See docs/API_CATALOG.md &sect;3 (Milestone 4 — jd-service). Only TEXT intake is implemented. */
@RestController
@RequestMapping("/api/jd")
public class JdController {

    private final JdService jdService;
    private final ai.careerforge.jd.service.JdOptimizationService jdOptimizationService;
    private final ai.careerforge.jd.repository.JobDescriptionRepository jobDescriptions;

    public JdController(JdService jdService,
                        ai.careerforge.jd.service.JdOptimizationService jdOptimizationService,
                        ai.careerforge.jd.repository.JobDescriptionRepository jobDescriptions) {
        this.jdService = jdService;
        this.jdOptimizationService = jdOptimizationService;
        this.jobDescriptions = jobDescriptions;
    }

    @PostMapping
    public ResponseEntity<JdSummaryResponse> submit(@CallerId String userId,
                                                     @Valid @RequestBody SubmitJdRequest request) {
        JdService.Submission submission = jdService.submitText(userId, request.jobDescriptionText());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(submission.jobDescription()));
    }

    /** SSRF-guarded fetch — see {@code SsrfGuard} and ARCHITECTURE_DECISIONS.md ADR-015. */
    @PostMapping("/fetch-url")
    public ResponseEntity<JdSummaryResponse> fetchUrl(@CallerId String userId,
                                                       @Valid @RequestBody FetchUrlRequest request) {
        JdService.Submission submission = jdService.fetchUrl(userId, request.url());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(submission.jobDescription()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JdDetailResponse> get(@CallerId String userId, @PathVariable String id) {
        JobDescription jd = jdService.requireOwned(userId, id);
        return ResponseEntity.ok(toDetail(jd));
    }

    /** Replaces the JD's text with a new version — unconditional, no confirm gate to block it
     *  (see {@code JdService#editText}) — and returns the same shape {@code GET /{id}} does, so
     *  the frontend can just swap its cached JD detail for the response body. */
    @PutMapping("/{id}")
    public ResponseEntity<JdDetailResponse> edit(@CallerId String userId, @PathVariable String id,
                                                  @Valid @RequestBody EditJdRequest request) {
        JobDescription jd = jdService.editText(userId, id, request.jobDescriptionText());
        return ResponseEntity.ok(toDetail(jd));
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<JdAnalysisResponse> analysis(@CallerId String userId, @PathVariable String id) {
        JdAnalysis analysis = jdService.analyse(userId, id);
        return ResponseEntity.ok(new JdAnalysisResponse(
                analysis.jobDescriptionId(), analysis.title(), analysis.company(), analysis.seniority(),
                analysis.keywords(),
                analysis.requirements().stream()
                        .map(r -> new RequirementResponse(r.requirementId(), r.text(), r.type(), r.weight(), r.normalisedTerms()))
                        .toList()));
    }

    /**
     * Optimises the caller's verified profile against this job description's current text
     * (ADR-033/ADR-037 — no confirm step) — the operation that replaced resume/cover-letter
     * generation. Returns targeting data, never a document.
     *
     * <p>Re-reads an existing result for the same JD version rather than spending another AI
     * request; pass {@code refresh=true} to recompute, which is what a profile edit warrants.
     */
    @PostMapping("/{id}/optimize")
    public ResponseEntity<JdOptimizationResponse> optimize(
            @CallerId String userId, @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(toOptimization(jdOptimizationService.optimise(userId, id, refresh)));
    }

    /**
     * Deterministic skill-gap patch (ADR-038): re-checks the caller's current evidence (after
     * adding a skill from the Skill Gap screen) against this JD's already-computed optimization
     * and promotes any newly-covered requirement — <strong>zero Groq calls</strong>. The result
     * is marked {@code derived}/{@code stale} in its {@code optimisation} payload; a full,
     * freshly-adjudicated result is still available via {@code POST /optimize?refresh=true}.
     */
    @PostMapping("/{id}/optimize/patch")
    public ResponseEntity<JdOptimizationResponse> optimizePatch(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toOptimization(jdOptimizationService.patchWithLatestEvidence(userId, id)));
    }

    /** The current optimization for this JD, if one has been computed. 404 when none exists. */
    @GetMapping("/{id}/optimization")
    public ResponseEntity<JdOptimizationResponse> optimization(@CallerId String userId, @PathVariable String id) {
        return jdOptimizationService.findForJobDescription(userId, id)
                .map(result -> ResponseEntity.ok(toOptimization(result)))
                .orElseThrow(ai.careerforge.common.error.ApiException::notOwned);
    }

    private static JdOptimizationResponse toOptimization(ai.careerforge.jd.domain.JdOptimization result) {
        return new JdOptimizationResponse(result.id(), result.jobDescriptionId(),
                result.optimisation(), result.citedEvidenceIds(), result.createdAt());
    }

    @GetMapping
    public ResponseEntity<PageResponse<JdSummaryResponse>> list(
            @CallerId String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        Page<JobDescription> result = jobDescriptions.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, cappedSize));
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent().stream().map(JdController::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    private static JdSummaryResponse toSummary(JobDescription jd) {
        return new JdSummaryResponse(jd.id(), jd.status().name(), jd.sourceType(), jd.title(), jd.company(), jd.createdAt());
    }

    private JdDetailResponse toDetail(JobDescription jd) {
        JdVersion version = jdService.currentVersion(jd);
        return new JdDetailResponse(
                jd.id(), jd.status().name(), jd.sourceType(), jd.sourceUrl(),
                jd.title(), jd.company(), jd.location(), jd.skillsSummary(), jd.experienceSummary(),
                version.rawText(), jd.currentVersion(), jd.createdAt());
    }
}
