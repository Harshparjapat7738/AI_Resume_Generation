package ai.careerforge.document.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.document.api.dto.DocumentRequests.RenderRequest;
import ai.careerforge.document.api.dto.DocumentResponses.RenderedDocumentResponse;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.service.DocumentRenderService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * See docs/API_CATALOG.md &sect;2 (document-service — real Resume PDF generation only;
 * cover-letter/DOCX rendering remain out of scope for this slice).
 *
 * <p>The selectable template catalogue lives at resume-service's
 * {@code GET /api/resumes/templates} (ADR-004, ADR-016) — this service only renders against
 * the ids that catalogue already serves, so it exposes no catalogue endpoint of its own.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRenderService renderService;

    public DocumentController(DocumentRenderService renderService) {
        this.renderService = renderService;
    }

    /** Renders (or re-renders, if the template changed) the resume version's PDF. Idempotent
     *  for an unchanged template against unchanged source content. */
    @PostMapping("/resume-versions/{resumeVersionId}/render")
    public ResponseEntity<RenderedDocumentResponse> render(
            @CallerId String userId, @PathVariable String resumeVersionId, @RequestBody(required = false) RenderRequest request) {
        String templateId = request == null ? null : request.templateId();
        RenderedDocument document = renderService.renderPdf(userId, resumeVersionId, templateId);
        return ResponseEntity.ok(toResponse(document));
    }

    /** Metadata for an already-rendered PDF, so the result page can offer a download without
     *  re-rendering. {@code 404} if this resume version has never been rendered — the
     *  frontend shows "PDF unavailable for this older generation" rather than an error. */
    @GetMapping("/resume-versions/{resumeVersionId}")
    public ResponseEntity<RenderedDocumentResponse> getForResumeVersion(
            @CallerId String userId, @PathVariable String resumeVersionId) {
        return ResponseEntity.ok(toResponse(renderService.requireExisting(userId, resumeVersionId)));
    }

    /**
     * Streams the PDF bytes through this service — MinIO is never reached directly from the
     * browser, so no storage endpoint or credential is ever exposed to it, and no filesystem
     * path is ever part of this URL (the id is an opaque Mongo id, the object key inside
     * MinIO is a separate random UUID the client never sees).
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@CallerId String userId, @PathVariable String id) {
        RenderedDocument document = renderService.requireOwnedDocument(userId, id);
        byte[] bytes = renderService.downloadBytes(document);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("resume.pdf").build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private static RenderedDocumentResponse toResponse(RenderedDocument document) {
        return new RenderedDocumentResponse(
                document.id(), document.resumeVersionId(), document.format().name(),
                document.templateId(), document.templateVersion(), document.pageCount(),
                document.byteSize(), document.sha256(), document.renderedAt());
    }
}
