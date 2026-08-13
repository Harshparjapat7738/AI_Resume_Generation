package ai.careerforge.resume.service;

import ai.careerforge.common.error.ApiError;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.resume.client.ClientDtos.CustomTemplateAssetDto;
import ai.careerforge.resume.client.ClientDtos.DetectedFieldDto;
import ai.careerforge.resume.client.DocumentServiceClient;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Template catalogue reads plus the selection guard generation relies on, plus — new in this
 * slice — everything a custom-upload template's owner can do to it. Ownership is checked here
 * so it applies uniformly however a caller reaches generation — see
 * {@link ai.careerforge.resume.domain.Template#isSelectableBy(String)}.
 *
 * <p>Custom-template mutations (upload/rename/duplicate/delete) call document-service's asset
 * API for whatever touches the actual file (store/duplicate the DOCX bytes, run analysis) —
 * see {@link DocumentServiceClient} — while everything about the catalogue row itself (name,
 * type, mapping, default flag, visibility) is decided and persisted here. document-service
 * never needs to know a template's display name; resume-service never needs to touch a byte of
 * the file.
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    /** Selected when generation doesn't specify a templateId — keeps the existing
     *  no-template-selected generation path working (backward compatible). */
    public static final String DEFAULT_TEMPLATE_ID = "classic";

    private static final long MAX_UPLOAD_SIZE = 5L * 1024 * 1024;

    private final TemplateRepository templates;
    private final DocumentServiceClient documentServiceClient;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templates, DocumentServiceClient documentServiceClient,
                           ObjectMapper objectMapper) {
        this.templates = templates;
        this.documentServiceClient = documentServiceClient;
        this.objectMapper = objectMapper;
    }

    /** Built-ins (visible to everyone) plus the caller's own custom uploads for this type —
     *  never another user's. Two queries rather than one because "type matches AND (built-in
     *  OR owned-by-me)" isn't expressible as a single Spring Data derived-method condition
     *  without a custom {@code Criteria} query, and two simple indexed lookups merged in
     *  memory is easier to reason about than a hand-written Mongo query for so small a result
     *  set. */
    public List<Template> list(String userId, TemplateType type) {
        List<Template> builtIns = templates.findByTypeAndStatusOrderByNameAsc(type, TemplateStatus.ACTIVE);
        if (userId == null || userId.isBlank()) {
            return builtIns;
        }
        List<Template> mine = templates.findByOwnerUserIdAndTypeAndStatusOrderByCreatedAtDesc(
                userId, type, TemplateStatus.ACTIVE);
        List<Template> combined = new ArrayList<>(builtIns);
        combined.addAll(mine);
        return combined;
    }

    /** 404s (never 403 — ADR-007 BOLA hardening) for a missing, disabled, or unauthorized
     *  template, exactly like every other ownership check in this codebase. */
    public Template get(String userId, String id) {
        Template template = templates.findById(id).orElseThrow(ApiException::notOwned);
        if (!template.isActive() || !template.isSelectableBy(userId)) {
            throw ApiException.notOwned();
        }
        return template;
    }

    /** Resolves the template to stamp on a generation. A blank id defaults to
     *  {@link #DEFAULT_TEMPLATE_ID} so callers that don't select a template yet keep working. */
    public Template resolveForGeneration(String userId, String templateId) {
        String effectiveId = (templateId == null || templateId.isBlank()) ? DEFAULT_TEMPLATE_ID : templateId;
        return get(userId, effectiveId);
    }

    // ---- custom-template mutations ---------------------------------------------------------

    public Template uploadCustom(String userId, String name, TemplateType type, byte[] bytes, String originalFilename) {
        validateUploadRequest(name, bytes);
        CustomTemplateAssetDto asset = callDocumentService(
                () -> documentServiceClient.store(encode(originalFilename), bytes),
                "Could not analyze the uploaded template. Please try again.");

        Map<String, Object> structureMap = toMap(asset.structure());
        List<Map<String, Object>> detectedFieldMaps = toDetectedFieldMaps(asset.detectedFields());
        Map<String, String> suggestedMapping = suggestMapping(asset.detectedFields());

        Template template = Template.forCustomUpload(
                asset.id(), userId, name.trim(), type, asset.originalFilename(), asset.format(),
                structureMap, detectedFieldMaps, suggestedMapping);
        return templates.save(template);
    }

    public Template rename(String userId, String id, String newName) {
        Template template = requireOwnedCustom(userId, id);
        template.rename(newName.trim());
        return templates.save(template);
    }

    public Template updateMapping(String userId, String id, Map<String, String> mappings) {
        Template template = requireOwnedCustom(userId, id);
        template.updateMapping(mappings == null ? Map.of() : mappings);
        return templates.save(template);
    }

    public Template duplicate(String userId, String id) {
        Template original = requireOwnedCustom(userId, id);
        CustomTemplateAssetDto copy = callDocumentService(
                () -> documentServiceClient.duplicate(original.id()),
                "Could not duplicate this template. Please try again.");

        Template duplicated = Template.forCustomUpload(
                copy.id(), userId, original.name() + " (copy)", original.type(), copy.originalFilename(),
                copy.format(), toMap(copy.structure()), toDetectedFieldMaps(copy.detectedFields()), original.fieldMappings());
        return templates.save(duplicated);
    }

    public void delete(String userId, String id) {
        Template template = requireOwnedCustom(userId, id);
        try {
            documentServiceClient.delete(template.id());
        } catch (FeignException ex) {
            // Best-effort, mirrors ObjectStorageService's own delete semantics: an orphaned
            // asset can be swept up later, but a document-service hiccup must never block the
            // user from removing the template from their own catalogue.
            log.warn("document-service asset delete failed for templateId={}: {}", template.id(), ex.getMessage());
        }
        templates.delete(template);
    }

    public Template setDefault(String userId, String id, boolean value) {
        Template template = requireOwnedCustom(userId, id);
        if (value) {
            for (Template other : templates.findByOwnerUserIdAndTypeAndStatusOrderByCreatedAtDesc(
                    userId, template.type(), TemplateStatus.ACTIVE)) {
                if (other.isDefault() && !other.id().equals(template.id())) {
                    other.setDefault(false);
                    templates.save(other);
                }
            }
        }
        template.setDefault(value);
        return templates.save(template);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private Template requireOwnedCustom(String userId, String id) {
        Template template = get(userId, id);
        if (!template.isCustom()) {
            // A built-in template can't be renamed/deleted/etc — same 404-not-403 BOLA
            // hardening as every other ownership failure in this codebase, rather than a
            // distinct "not allowed" error that would confirm the id refers to something real.
            throw ApiException.notOwned();
        }
        return template;
    }

    private void validateUploadRequest(String name, byte[] bytes) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A template name is required.");
        }
        if (name.trim().length() > 120) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The template name is too long.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file is empty.");
        }
        if (bytes.length > MAX_UPLOAD_SIZE) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file exceeds the 5 MB limit.");
        }
    }

    private interface DocumentServiceCall {
        CustomTemplateAssetDto call();
    }

    private CustomTemplateAssetDto callDocumentService(DocumentServiceCall call, String safeMessage) {
        try {
            return call.call();
        } catch (FeignException.BadRequest ex) {
            // document-service's own validation (wrong/mismatched format, no placeholders in a
            // PDF, too large, bad filename, ...) rejected it. document-service's ErrorCode
            // messages are already written as "safe, client-facing wording" (see ErrorCode's own
            // javadoc) — forwarding the real one gives a far more actionable reason ("No
            // {{placeholder}} fields were found in this PDF's text", say) than a generic string
            // ever could, and — critically — never hardcodes a single format the way the
            // previous ".docx file" message did once PDF became a second accepted format
            // (ADR-023). Falls back to a format-neutral generic message if the body can't be
            // parsed for any reason, never to a wrong, misleading one.
            throw new ApiException(ErrorCode.FILE_REJECTED, upstreamMessage(ex,
                    "The uploaded file was rejected. Make sure it's a valid .docx or .pdf file under 5 MB."));
        } catch (FeignException ex) {
            log.warn("document-service call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, safeMessage);
        }
    }

    private String upstreamMessage(FeignException.BadRequest ex, String fallback) {
        try {
            ApiError body = objectMapper.readValue(ex.contentUTF8(), ApiError.class);
            return (body.message() == null || body.message().isBlank()) ? fallback : body.message();
        } catch (Exception parseFailure) {
            return fallback;
        }
    }

    private Map<String, String> suggestMapping(List<DetectedFieldDto> detectedFields) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (detectedFields == null) return mapping;
        for (DetectedFieldDto field : detectedFields) {
            if (field.suggestedField() != null) {
                mapping.put(field.token(), field.suggestedField());
            }
        }
        return mapping;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) return Map.of();
        return objectMapper.convertValue(value, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toDetectedFieldMaps(List<DetectedFieldDto> detectedFields) {
        if (detectedFields == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (DetectedFieldDto field : detectedFields) {
            result.add(objectMapper.convertValue(field, Map.class));
        }
        return result;
    }

    private static String encode(String filename) {
        String safe = filename == null || filename.isBlank() ? "template.docx" : filename;
        return URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
