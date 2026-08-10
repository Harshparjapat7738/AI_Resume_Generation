package ai.careerforge.jd.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.jd.api.dto.JdRequests.SubmitJdRequest;
import ai.careerforge.jd.api.dto.JdResponses.JdAnalysisResponse;
import ai.careerforge.jd.api.dto.JdResponses.JdDetailResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** See docs/API_CATALOG.md &sect;3 (Milestone 4 — jd-service). Only TEXT intake is implemented. */
@RestController
@RequestMapping("/api/jd")
public class JdController {

    private final JdService jdService;
    private final ai.careerforge.jd.repository.JobDescriptionRepository jobDescriptions;

    public JdController(JdService jdService, ai.careerforge.jd.repository.JobDescriptionRepository jobDescriptions) {
        this.jdService = jdService;
        this.jobDescriptions = jobDescriptions;
    }

    @PostMapping
    public ResponseEntity<JdSummaryResponse> submit(@CallerId String userId,
                                                     @Valid @RequestBody SubmitJdRequest request) {
        JdService.Submission submission = jdService.submitText(userId, request.jobDescriptionText());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(submission.jobDescription()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JdDetailResponse> get(@CallerId String userId, @PathVariable String id) {
        JobDescription jd = jdService.requireOwned(userId, id);
        JdVersion version = jdService.currentVersion(jd);
        return ResponseEntity.ok(new JdDetailResponse(
                jd.id(), jd.status().name(), jd.sourceType(), jd.title(), jd.company(),
                version.rawText(), jd.currentVersion(), jd.createdAt()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<JdSummaryResponse> confirm(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toSummary(jdService.confirm(userId, id)));
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
}
