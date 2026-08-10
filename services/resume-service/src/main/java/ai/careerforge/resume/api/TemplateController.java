package ai.careerforge.resume.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.resume.api.dto.TemplateResponses.TemplateResponse;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.service.TemplateService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Selectable template catalogue — docs/API_CATALOG.md &sect;3 (Milestone 5,
 * {@code GET /api/resumes/templates}). Only built-in templates exist today; see
 * ARCHITECTURE_DECISIONS.md ADR-016 for why upload and online templates aren't served here yet.
 */
@RestController
@RequestMapping("/api/resumes/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list(
            @RequestParam(defaultValue = "RESUME") TemplateType type) {
        List<TemplateResponse> response = templateService.list(type).stream()
                .map(TemplateController::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> get(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.get(userId, id)));
    }

    private static TemplateResponse toResponse(Template template) {
        return new TemplateResponse(
                template.id(),
                template.name(),
                template.description(),
                template.previewKey(),
                template.type().name(),
                template.version(),
                template.status().name(),
                template.source().name(),
                template.supportedFormats(),
                template.atsSafe());
    }
}
