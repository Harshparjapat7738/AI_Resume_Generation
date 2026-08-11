package ai.careerforge.document.api;

import ai.careerforge.common.security.CallerId;
import ai.careerforge.document.api.dto.CustomTemplateAssetRequests.GenerateRequest;
import ai.careerforge.document.api.dto.CustomTemplateAssetResponses.CustomTemplateAssetResponse;
import ai.careerforge.document.api.dto.CustomTemplateAssetResponses.DetectedFieldResponse;
import ai.careerforge.document.api.dto.CustomTemplateAssetResponses.TemplateStructureResponse;
import ai.careerforge.document.api.dto.DocumentResponses.RenderedDocumentResponse;
import ai.careerforge.document.domain.CustomTemplateAsset;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.domain.TemplateStructure;
import ai.careerforge.document.service.CustomTemplateAssetService;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The renderable-asset half of custom templates (see {@link CustomTemplateAssetService}'s own
 * class comment for the resume-service/document-service split). Reached only by resume-service
 * (via its {@code DocumentServiceClient} Feign client, caller-id auto-forwarded — see
 * FeignHeaderForwardingConfig) for store/duplicate/delete — the browser never uploads here
 * directly, it always goes through resume-service's own multipart endpoint, which is why
 * {@code store} takes a plain byte body rather than {@code multipart/form-data} (no Feign
 * multipart encoder needed for a single internal caller). {@code generate} is the one endpoint
 * the frontend calls directly, mirroring the existing built-in PDF render endpoint's shape.
 */
@RestController
@RequestMapping("/api/documents/custom-templates")
public class CustomTemplateAssetController {

    private final CustomTemplateAssetService assetService;

    public CustomTemplateAssetController(CustomTemplateAssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    public ResponseEntity<CustomTemplateAssetResponse> store(
            @CallerId String userId, @RequestHeader("X-Filename") String encodedFilename, @RequestBody byte[] file) {
        String filename = URLDecoder.decode(encodedFilename, StandardCharsets.UTF_8);
        CustomTemplateAsset asset = assetService.storeAndAnalyze(userId, file, filename);
        return ResponseEntity.ok(toResponse(asset));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<CustomTemplateAssetResponse> duplicate(@CallerId String userId, @PathVariable String id) {
        return ResponseEntity.ok(toResponse(assetService.duplicate(userId, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CallerId String userId, @PathVariable String id) {
        assetService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Profile data (via resume+profile fetch) → this custom template → mail-merge → a new
     *  DOCX, downloadable through the same {@code GET /api/documents/{id}/download} endpoint
     *  the built-in PDF pipeline already uses. */
    @PostMapping("/{id}/generate")
    public ResponseEntity<RenderedDocumentResponse> generate(
            @CallerId String userId, @PathVariable String id, @Valid @RequestBody GenerateRequest request) {
        RenderedDocument document = assetService.generate(userId, id, request.resumeVersionId(), request.fieldMappings());
        return ResponseEntity.ok(new RenderedDocumentResponse(
                document.id(), document.resumeVersionId(), document.format().name(), document.templateId(),
                document.templateVersion(), document.pageCount(), document.byteSize(), document.sha256(), document.renderedAt()));
    }

    private static CustomTemplateAssetResponse toResponse(CustomTemplateAsset asset) {
        return new CustomTemplateAssetResponse(
                asset.id(), asset.originalFilename(), asset.byteSize(), asset.sha256(),
                toStructureResponse(asset.structure()),
                asset.detectedFields().stream()
                        .map(CustomTemplateAssetController::toFieldResponse)
                        .toList(),
                asset.createdAt());
    }

    private static TemplateStructureResponse toStructureResponse(TemplateStructure s) {
        if (s == null) return null;
        return new TemplateStructureResponse(
                s.pageWidthTwips(), s.pageHeightTwips(), s.marginTopTwips(), s.marginBottomTwips(),
                s.marginLeftTwips(), s.marginRightTwips(), s.columnCount(), s.fontsUsed(), s.fontSizesPt(),
                s.colorsUsedHex(), s.alignmentsUsed(), s.headingsFound(), s.paragraphCount(), s.tableCount(),
                s.hasHeader(), s.hasFooter());
    }

    private static DetectedFieldResponse toFieldResponse(DetectedField f) {
        return new DetectedFieldResponse(f.token(), f.context(), f.suggestedField());
    }
}
