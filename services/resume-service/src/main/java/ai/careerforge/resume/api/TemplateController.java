package ai.careerforge.resume.api;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.common.security.CallerId;
import ai.careerforge.resume.api.dto.TemplateRequests.RenameTemplateRequest;
import ai.careerforge.resume.api.dto.TemplateRequests.UpdateMappingRequest;
import ai.careerforge.resume.api.dto.TemplateResponses.TemplateResponse;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.service.TemplateService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Selectable template catalogue — docs/API_CATALOG.md &sect;3 (Milestone 5,
 * {@code GET /api/resumes/templates}) — now including a caller's own custom uploads alongside
 * the built-in catalogue (see {@link TemplateService#list}). Every mutating endpoint below is
 * scoped to {@code CUSTOM_UPLOAD} templates the caller owns; a built-in template 404s exactly
 * like someone else's custom one would (ADR-007 BOLA hardening — never a distinct "not
 * allowed" response that would confirm the id refers to something real).
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
            @CallerId String userId, @RequestParam(defaultValue = "RESUME") TemplateType type) {
        List<TemplateResponse> response = templateService.list(userId, type).stream()
                .map(TemplateController::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> get(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.get(userId, id)));
    }

    /** Upload flow step 1 (of the wizard docs/API_CATALOG.md describes for this feature):
     *  choose file + type + name → this call validates, forwards the bytes to document-service
     *  for storage and real structural analysis, and returns the new catalogue row already
     *  carrying the analyzer's detected placeholders and suggested mapping — the frontend's
     *  mapping-editor step needs no separate "analyze" call. */
    @PostMapping("/custom")
    public ResponseEntity<TemplateResponse> uploadCustom(
            @CallerId String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "type", defaultValue = "RESUME") TemplateType type) {
        byte[] bytes = readBytes(file);
        Template template = templateService.uploadCustom(userId, name, type, bytes, file.getOriginalFilename());
        return ResponseEntity.ok(toResponse(template));
    }

    @PutMapping("/custom/{id}")
    public ResponseEntity<TemplateResponse> rename(
            @CallerId String userId, @PathVariable String id, @Valid @RequestBody RenameTemplateRequest request) {
        return ResponseEntity.ok(toResponse(templateService.rename(userId, id, request.name())));
    }

    @PutMapping("/custom/{id}/mapping")
    public ResponseEntity<TemplateResponse> updateMapping(
            @CallerId String userId, @PathVariable String id, @RequestBody UpdateMappingRequest request) {
        return ResponseEntity.ok(toResponse(templateService.updateMapping(userId, id, request.mappings())));
    }

    @PostMapping("/custom/{id}/duplicate")
    public ResponseEntity<TemplateResponse> duplicate(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.duplicate(userId, id)));
    }

    @DeleteMapping("/custom/{id}")
    public ResponseEntity<Void> delete(@CallerId String userId, @PathVariable String id) {
        templateService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/custom/{id}/default")
    public ResponseEntity<TemplateResponse> setDefault(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.setDefault(userId, id, true)));
    }

    @DeleteMapping("/custom/{id}/default")
    public ResponseEntity<TemplateResponse> unsetDefault(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.setDefault(userId, id, false)));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "No file was uploaded.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file could not be read.");
        }
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
                template.atsSafe(),
                template.ownerUserId(),
                template.originalFilename(),
                template.structure(),
                template.detectedFields(),
                template.fieldMappings(),
                template.isDefault(),
                template.createdAt());
    }
}
