package ai.careerforge.resume.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.resume.api.dto.ResumeRequests.GenerateRequest;
import ai.careerforge.resume.api.dto.ResumeResponses.GapResponse;
import ai.careerforge.resume.api.dto.ResumeResponses.PageResponse;
import ai.careerforge.resume.api.dto.ResumeResponses.ResumeSummaryResponse;
import ai.careerforge.resume.api.dto.ResumeResponses.ResumeVersionResponse;
import ai.careerforge.resume.domain.ResumeVersion;
import ai.careerforge.resume.repository.ResumeVersionRepository;
import ai.careerforge.resume.service.ResumeGenerationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * See docs/API_CATALOG.md &sect;3 (Milestone 5 — resume-service).
 *
 * <p>{@code POST /generate} deviates from the documented async 202-plus-job-id contract —
 * see {@link ResumeGenerationService} and ARCHITECTURE_DECISIONS.md ADR-013.
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeGenerationService generationService;
    private final ResumeVersionRepository versions;

    public ResumeController(ResumeGenerationService generationService, ResumeVersionRepository versions) {
        this.generationService = generationService;
        this.versions = versions;
    }

    @PostMapping("/generate")
    public ResponseEntity<ResumeVersionResponse> generate(
            @CallerId String userId, @Valid @RequestBody GenerateRequest request) {
        ResumeVersion version = generationService.generate(userId, request.jobDescriptionId(), request.templateId());
        return ResponseEntity.ok(toResponse(version));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeVersionResponse> get(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(generationService.requireOwned(userId, id)));
    }

    /** Generation history for the /dashboard screen. */
    @GetMapping
    public ResponseEntity<PageResponse<ResumeSummaryResponse>> list(
            @CallerId String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        Page<ResumeVersion> result = versions.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, cappedSize));
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent().stream().map(ResumeController::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    private static ResumeSummaryResponse toSummary(ResumeVersion version) {
        return new ResumeSummaryResponse(
                version.id(), version.jobDescriptionId(), version.jobTitle(), version.company(),
                version.templateId(), version.createdAt());
    }

    private static ResumeVersionResponse toResponse(ResumeVersion version) {
        List<GapResponse> gaps = version.gaps() == null ? List.of() : version.gaps().stream()
                .map(g -> new GapResponse(
                        String.valueOf(g.get("requirementId")), String.valueOf(g.get("text")), String.valueOf(g.get("type"))))
                .toList();
        return new ResumeVersionResponse(
                version.id(), version.jobDescriptionId(), version.jobTitle(), version.company(),
                version.templateId(), version.templateVersion(), version.content(),
                version.evidenceMatches(), gaps, version.groundingReport(), version.removedSections(),
                version.createdAt());
    }
}
