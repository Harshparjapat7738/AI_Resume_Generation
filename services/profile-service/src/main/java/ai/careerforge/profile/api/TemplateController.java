package ai.careerforge.profile.api;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.common.security.CallerId;
import ai.careerforge.profile.api.dto.TemplateRequests.RenameTemplateRequest;
import ai.careerforge.profile.api.dto.TemplateResponses.TemplateResponse;
import ai.careerforge.profile.domain.Template;
import ai.careerforge.profile.domain.TemplateDocumentType;
import ai.careerforge.profile.service.TemplateService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * "My Templates" (ADR-034) — a user's own uploaded Resume/Cover Letter files, browsed and
 * managed here, then referenced (never re-uploaded) at JD-optimization handoff time. See
 * docs/API_CATALOG.md &sect;3 for the full contract.
 */
@RestController
@RequestMapping("/api/profile/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list(@CallerId String userId) {
        return ResponseEntity.ok(templateService.list(userId).stream().map(TemplateController::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> get(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.requireOwned(userId, id)));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> upload(
            @CallerId String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "documentType", defaultValue = "RESUME") TemplateDocumentType documentType) {
        byte[] bytes = readBytes(file);
        Template template = templateService.upload(userId, bytes, file.getOriginalFilename(), name, documentType);
        return ResponseEntity.ok(toResponse(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> rename(
            @CallerId String userId, @PathVariable String id, @Valid @RequestBody RenameTemplateRequest request) {
        return ResponseEntity.ok(toResponse(templateService.rename(userId, id, request.name())));
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<TemplateResponse> setDefault(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(templateService.setDefault(userId, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CallerId String userId, @PathVariable String id) {
        templateService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Streams the original bytes back exactly as uploaded — no rendering, no transformation.
     *  The only way to reach a stored file: no public URL exists (ADR-034). */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@CallerId String userId, @PathVariable String id) {
        Template template = templateService.requireOwned(userId, id);
        byte[] bytes = templateService.download(userId, id);
        String contentType = templateService.contentTypeFor(template);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(template.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
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
                template.originalFilename(),
                template.fileType().name(),
                template.documentType().name(),
                template.isDefault(),
                template.byteSize(),
                template.createdAt(),
                template.updatedAt());
    }
}
