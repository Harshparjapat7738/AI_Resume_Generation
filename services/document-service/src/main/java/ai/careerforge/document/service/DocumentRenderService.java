package ai.careerforge.document.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ClientDtos.TemplateFieldMappingDto;
import ai.careerforge.document.client.ProfileServiceClient;
import ai.careerforge.document.client.ResumeServiceClient;
import ai.careerforge.document.client.TemplateServiceClient;
import ai.careerforge.document.domain.CustomTemplateAsset;
import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.DocumentType;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.render.PdfRenderer;
import ai.careerforge.document.render.PdfRenderer.RenderedPdf;
import ai.careerforge.document.render.ResumeRenderModel;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
import ai.careerforge.document.render.SampleResumeRenderModel;
import ai.careerforge.document.render.TemplatePreviewResult;
import ai.careerforge.document.repository.RenderedDocumentRepository;
import ai.careerforge.document.template.ResumeTemplate;
import feign.FeignException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates PDF rendering: pulls the already-generated, already-grounded resume
 * (resume-service) and the candidate's full profile (profile-service), merges them into a
 * render model, renders the chosen template to PDF, and persists the artifact. Mirrors
 * assessment-service's {@code AssessmentService} fetch/cache pattern.
 *
 * <p>Idempotent per (resumeVersionId, format): re-rendering with the same template against
 * unchanged source data returns the existing artifact rather than re-uploading; rendering
 * with a different template (or after the underlying resume content changed) replaces it —
 * there is one current PDF per resume version, not an accumulating history.
 */
@Service
public class DocumentRenderService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderService.class);

    private final ResumeServiceClient resumeServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final TemplateServiceClient templateServiceClient;
    private final ResumeRenderModelBuilder modelBuilder;
    private final PdfRenderer pdfRenderer;
    private final ObjectStorageService storage;
    private final RenderedDocumentRepository documents;
    private final CustomTemplateAssetService customTemplateAssets;

    public DocumentRenderService(ResumeServiceClient resumeServiceClient, ProfileServiceClient profileServiceClient,
                                 TemplateServiceClient templateServiceClient, ResumeRenderModelBuilder modelBuilder,
                                 PdfRenderer pdfRenderer, ObjectStorageService storage,
                                 RenderedDocumentRepository documents, CustomTemplateAssetService customTemplateAssets) {
        this.resumeServiceClient = resumeServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.templateServiceClient = templateServiceClient;
        this.modelBuilder = modelBuilder;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.documents = documents;
        this.customTemplateAssets = customTemplateAssets;
    }

    /**
     * Renders (or re-renders) the resume version's document. {@code effectiveTemplateId} may
     * name either a built-in template (the original, only path this method had) or — since
     * ADR-023 — one of the caller's own custom-uploaded templates (DOCX or PDF): the wizard's
     * template-selection step now offers both, and {@code POST /api/resumes/generate} already
     * accepted and persisted a custom {@code templateId} on the resume version before this
     * dispatch existed (see ADR-023's "Problem" for why that half was never actually missing).
     * A custom-template render delegates entirely to {@link CustomTemplateAssetService#generate}
     * — the exact same method the dedicated {@code /custom-templates/{id}/generate} endpoint
     * already calls — so there is only ever one place that performs a custom-template merge.
     */
    public RenderedDocument renderPdf(String userId, String resumeVersionId, String templateId) {
        ResumeVersionDto resume = fetchOwnedResume(resumeVersionId);
        // An explicit templateId (a caller wanting a different look than what was generated
        // with) wins; otherwise render with whatever was actually selected in the wizard at
        // generation time (ADR-016) — never a document-service-local default while that's
        // available. Only a resume version that predates template selection entirely (no
        // templateId stored at all) falls through to ResumeTemplate.DEFAULT.
        String effectiveTemplateId = hasText(templateId) ? templateId : resume.templateId();

        Optional<CustomTemplateAsset> customTemplate = hasText(effectiveTemplateId)
                ? customTemplateAssets.findOwned(userId, effectiveTemplateId)
                : Optional.empty();
        if (customTemplate.isPresent()) {
            Map<String, String> fieldMappings = fetchFieldMappings(userId, effectiveTemplateId);
            return customTemplateAssets.generate(userId, effectiveTemplateId, resumeVersionId, fieldMappings);
        }

        ResumeTemplate template = ResumeTemplate.fromId(effectiveTemplateId);
        ProfileDto profile = fetchProfile();

        ResumeRenderModel model = modelBuilder.build(resume, profile);
        RenderedPdf rendered = pdfRenderer.render(template, model);
        String sha256 = sha256Hex(rendered.bytes());

        Optional<RenderedDocument> existing =
                documents.findByResumeVersionIdAndFormatAndUserId(resumeVersionId, DocumentFormat.PDF, userId);

        if (existing.isPresent() && sha256.equals(existing.get().sha256()) && template.id().equals(existing.get().templateId())) {
            return existing.get();
        }

        String objectKey = storage.upload(rendered.bytes(), "application/pdf");

        if (existing.isPresent()) {
            RenderedDocument doc = existing.get();
            String previousObjectKey = doc.objectKey();
            doc.replaceWith(objectKey, storage.bucket(), sha256, rendered.bytes().length, rendered.pageCount(),
                    template.id(), template.version(), PdfRenderer.ENGINE_VERSION);
            RenderedDocument saved = documents.save(doc);
            storage.delete(previousObjectKey);
            return saved;
        }

        RenderedDocument doc = new RenderedDocument(userId, resumeVersionId, DocumentType.RESUME, DocumentFormat.PDF,
                objectKey, storage.bucket(), sha256, rendered.bytes().length, rendered.pageCount(),
                template.id(), template.version(), PdfRenderer.ENGINE_VERSION);
        return documents.save(doc);
    }

    /**
     * The Templates page's real, shared-renderer preview (redesign brief &sect;2/14) — "what
     * will my resume actually look like in this template", answered with the exact same
     * {@link PdfRenderer}/mail-merge code {@link #renderPdf} uses for a real generation, just
     * fed {@link SampleResumeRenderModel#sample()} instead of an owned resume + profile. No
     * {@code resumeVersionId} exists yet at this point in the workflow, so — unlike
     * {@link #renderPdf} — nothing here is persisted as a {@link RenderedDocument}; the
     * controller streams the bytes straight back and a repeat visit simply re-renders (cheap:
     * the built-in path is one in-memory Thymeleaf+PDF render, the custom path is one
     * mail-merge, neither hits an LLM). Dispatch mirrors {@link #renderPdf} exactly: the
     * caller's own custom templates first, a built-in id otherwise — a `templateId` that's
     * neither (someone else's custom template, or a typo) surfaces the same "unknown template"
     * validation error {@link ai.careerforge.document.template.ResumeTemplate#fromId} already
     * throws, never another user's asset.
     */
    public TemplatePreviewResult renderPreview(String userId, String templateId) {
        Optional<CustomTemplateAsset> customTemplate = hasText(templateId)
                ? customTemplateAssets.findOwned(userId, templateId)
                : Optional.empty();
        if (customTemplate.isPresent()) {
            Map<String, String> fieldMappings = fetchFieldMappings(userId, templateId);
            return customTemplateAssets.generatePreview(customTemplate.get(), fieldMappings);
        }

        ResumeTemplate template = ResumeTemplate.fromId(templateId);
        RenderedPdf rendered = pdfRenderer.render(template, SampleResumeRenderModel.sample());
        return new TemplatePreviewResult(rendered.bytes(), DocumentFormat.PDF);
    }

    public RenderedDocument requireExisting(String userId, String resumeVersionId) {
        return documents.findByResumeVersionIdAndFormatAndUserId(resumeVersionId, DocumentFormat.PDF, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public RenderedDocument requireOwnedDocument(String userId, String documentId) {
        return documents.findByIdAndUserId(documentId, userId).orElseThrow(ApiException::notOwned);
    }

    public byte[] downloadBytes(RenderedDocument document) {
        return storage.download(document.objectKey());
    }

    /**
     * Ownership is enforced entirely upstream: resume-service's {@code GET /api/resumes/{id}}
     * is itself scoped to the caller (see {@code ResumeGenerationService.requireOwned}), and
     * document-service forwards the caller-id header on this Feign call
     * (FeignHeaderForwardingConfig) exactly as assessment-service does for the identical
     * call. A resume owned by someone else already 404s here, never a raw 403.
     */
    private ResumeVersionDto fetchOwnedResume(String resumeVersionId) {
        try {
            return resumeServiceClient.getResume(resumeVersionId);
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("resume-service call failed for resumeVersionId={}: {}", resumeVersionId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private ProfileDto fetchProfile() {
        try {
            return profileServiceClient.getProfile();
        } catch (FeignException ex) {
            log.warn("profile-service call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    /** The dedicated {@code /custom-templates/{id}/generate} endpoint receives a field mapping
     *  straight from the caller's own request body (the Templates page's mapping editor already
     *  ran) — this render endpoint never did, so it fetches the owner's saved mapping from
     *  resume-service instead. {@code GET /api/resumes/templates/{id}} is itself ownership-
     *  scoped, so this can never leak another user's mapping even though
     *  {@link #renderPdf} already separately confirmed {@code effectiveTemplateId} belongs to
     *  {@code userId} via {@code customTemplateAssets.findOwned}. */
    private Map<String, String> fetchFieldMappings(String userId, String templateId) {
        try {
            TemplateFieldMappingDto template = templateServiceClient.getTemplate(templateId);
            return template.fieldMappings() == null ? Map.of() : template.fieldMappings();
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException ex) {
            log.warn("resume-service call failed for templateId={}: {}", templateId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", ex);
        }
    }
}
