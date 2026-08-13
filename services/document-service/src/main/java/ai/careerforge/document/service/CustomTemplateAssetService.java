package ai.careerforge.document.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ProfileServiceClient;
import ai.careerforge.document.client.ResumeServiceClient;
import ai.careerforge.document.docx.DocxMailMerge;
import ai.careerforge.document.docx.DocxStructureAnalyzer;
import ai.careerforge.document.domain.CustomTemplateAsset;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.DocumentType;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.domain.TemplateFormat;
import ai.careerforge.document.domain.TemplateStructure;
import ai.careerforge.document.pdf.PdfMailMerge;
import ai.careerforge.document.pdf.PdfStructureAnalyzer;
import ai.careerforge.document.render.ResumeRenderModel;
import ai.careerforge.document.render.ResumeRenderModel.CertificationEntry;
import ai.careerforge.document.render.ResumeRenderModel.EducationEntry;
import ai.careerforge.document.render.ResumeRenderModel.ExperienceEntry;
import ai.careerforge.document.render.ResumeRenderModel.ProjectEntry;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
import ai.careerforge.document.render.SampleResumeRenderModel;
import ai.careerforge.document.render.TemplatePreviewResult;
import ai.careerforge.document.repository.CustomTemplateAssetRepository;
import ai.careerforge.document.repository.RenderedDocumentRepository;
import feign.FeignException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The asset half of the custom-template feature: validating and storing the raw upload,
 * running the real structural analysis, and — at generation time — performing the mail-merge
 * and persisting the result exactly like {@link DocumentRenderService} persists a rendered PDF
 * (same {@link RenderedDocument} collection, same idempotent-replace-per-format behavior, same
 * download endpoint). resume-service owns the *catalogue* row (name, type, mapping) a user
 * actually browses and edits — this service is only ever called by resume-service (via Feign)
 * or, for generation, by the frontend directly, mirroring how the built-in PDF render endpoint
 * is already called directly.
 */
@Service
public class CustomTemplateAssetService {

    private static final Logger log = LoggerFactory.getLogger(CustomTemplateAssetService.class);
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_MAIL_MERGE_ENGINE_VERSION = "docx4j-11.5.3+docx-mailmerge-1";
    private static final String PDF_MAIL_MERGE_ENGINE_VERSION = "pdfbox-3.0.3+pdf-mailmerge-1";
    /** Mirrors {@code spring.servlet.multipart.max-file-size} (application.yml,
     *  {@code MAX_UPLOAD_SIZE}, default 5MB) — enforced again here so this service rejects an
     *  oversized file with the same clear reason regardless of caller. */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final DocxStructureAnalyzer docxAnalyzer;
    private final DocxMailMerge docxMailMerge;
    private final PdfStructureAnalyzer pdfAnalyzer;
    private final PdfMailMerge pdfMailMerge;
    private final ObjectStorageService storage;
    private final CustomTemplateAssetRepository assets;
    private final RenderedDocumentRepository documents;
    private final ResumeServiceClient resumeServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final ResumeRenderModelBuilder modelBuilder;

    public CustomTemplateAssetService(DocxStructureAnalyzer docxAnalyzer, DocxMailMerge docxMailMerge,
                                       PdfStructureAnalyzer pdfAnalyzer, PdfMailMerge pdfMailMerge,
                                       ObjectStorageService storage, CustomTemplateAssetRepository assets,
                                       RenderedDocumentRepository documents, ResumeServiceClient resumeServiceClient,
                                       ProfileServiceClient profileServiceClient, ResumeRenderModelBuilder modelBuilder) {
        this.docxAnalyzer = docxAnalyzer;
        this.docxMailMerge = docxMailMerge;
        this.pdfAnalyzer = pdfAnalyzer;
        this.pdfMailMerge = pdfMailMerge;
        this.storage = storage;
        this.assets = assets;
        this.documents = documents;
        this.resumeServiceClient = resumeServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.modelBuilder = modelBuilder;
    }

    public CustomTemplateAsset storeAndAnalyze(String userId, byte[] bytes, String originalFilename) {
        TemplateFormat format = detectFormat(bytes, originalFilename);
        // If this can't genuinely be parsed as the format its extension/signature claims, the
        // matching analyzer's own `load`/`analyze` throws FILE_REJECTED — a corrupt, malicious,
        // or merely-mislabelled file never reaches storage. A PDF with no detectable
        // {{placeholder}} is rejected the same way (PdfStructureAnalyzer.analyze) — never
        // accepted as a template nothing could actually fill in.
        String contentType = format == TemplateFormat.PDF ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        TemplateStructure structure;
        List<DetectedField> detectedFields;
        if (format == TemplateFormat.PDF) {
            PDDocument document = pdfAnalyzer.load(bytes);
            try {
                PdfStructureAnalyzer.Analysis analysis = pdfAnalyzer.analyze(document);
                structure = analysis.structure();
                detectedFields = analysis.detectedFields();
            } finally {
                closeQuietly(document);
            }
        } else {
            WordprocessingMLPackage pkg = docxAnalyzer.load(bytes);
            DocxStructureAnalyzer.Analysis analysis = docxAnalyzer.analyze(pkg);
            structure = analysis.structure();
            detectedFields = analysis.detectedFields();
        }

        String sha256 = sha256Hex(bytes);
        String objectKey = storage.upload(bytes, contentType);
        CustomTemplateAsset asset = new CustomTemplateAsset(
                userId, sanitizeFilename(originalFilename), format, objectKey, bytes.length, sha256,
                structure, detectedFields);
        return assets.save(asset);
    }

    private void closeQuietly(PDDocument document) {
        try {
            document.close();
        } catch (java.io.IOException ex) {
            log.warn("Failed to close a PDF document after analysis: {}", ex.getMessage());
        }
    }

    public CustomTemplateAsset requireOwned(String userId, String id) {
        return assets.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /** Non-throwing lookup for {@code DocumentRenderService}'s dispatch (ADR-023): a
     *  {@code templateId} that simply isn't a custom asset at all (the overwhelmingly common
     *  case — a built-in template) is a normal, expected outcome there, not an ownership
     *  failure to reject with — only {@link #requireOwned} treats "not mine" as an error, for
     *  every caller that already knows it's asking about a custom template specifically. */
    public Optional<CustomTemplateAsset> findOwned(String userId, String id) {
        return assets.findByIdAndUserId(id, userId);
    }

    public CustomTemplateAsset duplicate(String userId, String id) {
        CustomTemplateAsset original = requireOwned(userId, id);
        byte[] bytes = storage.download(original.objectKey());
        String contentType = original.format() == TemplateFormat.PDF ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        String newObjectKey = storage.upload(bytes, contentType);
        CustomTemplateAsset copy = new CustomTemplateAsset(
                userId, original.originalFilename(), original.format(), newObjectKey, original.byteSize(),
                original.sha256(), original.structure(), original.detectedFields());
        return assets.save(copy);
    }

    /** Deletes the stored file. Any DOCX previously generated *from* this template stays
     *  downloadable by its own id (the bytes already produced don't depend on the source
     *  template surviving) — only the source asset and the ability to generate a new one from
     *  it goes away, mirroring how a deleted built-in template would still leave already
     *  rendered PDFs intact. */
    public void delete(String userId, String id) {
        CustomTemplateAsset asset = requireOwned(userId, id);
        storage.delete(asset.objectKey());
        assets.delete(asset);
    }

    /** Mail-merges (DOCX) or redact-and-overlay-merges (PDF, see {@code pdf.PdfMailMerge}) the
     *  already-generated, already-grounded resume version's content into this template. Called
     *  both by the dedicated {@code POST /api/documents/custom-templates/{id}/generate}
     *  endpoint (frontend-supplied {@code fieldMappings}, from the Templates page's own mapping
     *  editor) and, since ADR-023, by {@code DocumentRenderService}'s dispatch from the *main*
     *  render endpoint when {@code templateId} names one of the caller's own custom templates —
     *  one method, no duplicated rendering pipeline either way. */
    public RenderedDocument generate(String userId, String assetId, String resumeVersionId, Map<String, String> fieldMappings) {
        CustomTemplateAsset asset = requireOwned(userId, assetId);
        byte[] originalBytes = storage.download(asset.objectKey());

        ResumeVersionDto resume = fetchOwnedResume(resumeVersionId);
        ProfileDto profile = fetchProfile();
        ResumeRenderModel model = modelBuilder.build(resume, profile);
        Map<String, String> resolved = resolveValues(model, fieldMappings == null ? Map.of() : fieldMappings);

        boolean isPdf = asset.format() == TemplateFormat.PDF;
        DocumentFormat outputFormat = isPdf ? DocumentFormat.PDF : DocumentFormat.DOCX;
        String contentType = isPdf ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        String engineVersion = isPdf ? PDF_MAIL_MERGE_ENGINE_VERSION : DOCX_MAIL_MERGE_ENGINE_VERSION;
        byte[] merged = isPdf ? mergePdf(originalBytes, resolved) : mergeDocx(originalBytes, resolved);
        // pageCount: not computed for either custom-template format — DOCX has no reliable page
        // count without a separate rendering pass, and even for PDF (where PDFBox could report
        // it) the page count never changes across a mail-merge, so re-deriving it here would
        // only duplicate what the original upload's own analysis already knows nothing new. 0 is
        // a deliberate "not applicable" sentinel the frontend already treats as "unknown".
        int pageCount = 0;
        String sha256 = sha256Hex(merged);

        Optional<RenderedDocument> existing =
                documents.findByResumeVersionIdAndFormatAndUserId(resumeVersionId, outputFormat, userId);
        if (existing.isPresent() && sha256.equals(existing.get().sha256()) && assetId.equals(existing.get().templateId())) {
            return existing.get();
        }

        String objectKey = storage.upload(merged, contentType);
        if (existing.isPresent()) {
            RenderedDocument doc = existing.get();
            String previousObjectKey = doc.objectKey();
            doc.replaceWith(objectKey, storage.bucket(), sha256, merged.length, pageCount, assetId, "custom", engineVersion);
            RenderedDocument saved = documents.save(doc);
            storage.delete(previousObjectKey);
            return saved;
        }

        RenderedDocument doc = new RenderedDocument(userId, resumeVersionId, DocumentType.RESUME, outputFormat,
                objectKey, storage.bucket(), sha256, merged.length, pageCount, assetId, "custom", engineVersion);
        return documents.save(doc);
    }

    /**
     * The Templates page's "what will my resume look like in this template" preview (redesign
     * brief &sect;2/14) — the exact same mail-merge this template uses at real generation time
     * ({@link #generate}), just fed {@link SampleResumeRenderModel#sample()} instead of a real
     * resume+profile. Never persisted as a {@link RenderedDocument} (there's no
     * {@code resumeVersionId} to key it on, and it would need to be re-rendered the moment the
     * template's mapping changes anyway) — the controller streams the bytes straight back.
     */
    public TemplatePreviewResult generatePreview(CustomTemplateAsset asset, Map<String, String> fieldMappings) {
        byte[] originalBytes = storage.download(asset.objectKey());
        Map<String, String> resolved = resolveValues(SampleResumeRenderModel.sample(),
                fieldMappings == null ? Map.of() : fieldMappings);

        boolean isPdf = asset.format() == TemplateFormat.PDF;
        byte[] merged = isPdf ? mergePdf(originalBytes, resolved) : mergeDocx(originalBytes, resolved);
        return new TemplatePreviewResult(merged, isPdf ? DocumentFormat.PDF : DocumentFormat.DOCX);
    }

    private byte[] mergeDocx(byte[] originalBytes, Map<String, String> resolved) {
        WordprocessingMLPackage pkg = docxAnalyzer.load(originalBytes);
        return docxMailMerge.merge(pkg, resolved);
    }

    private byte[] mergePdf(byte[] originalBytes, Map<String, String> resolved) {
        PDDocument document = pdfAnalyzer.load(originalBytes);
        try {
            return pdfMailMerge.merge(document, resolved);
        } finally {
            closeQuietly(document);
        }
    }

    // ---- field resolution: mapped profile-field key -> flat text ready for the merge --------

    private Map<String, String> resolveValues(ResumeRenderModel model, Map<String, String> fieldMappings) {
        java.util.Map<String, String> resolved = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String token = mapping.getKey();
            String fieldKey = mapping.getValue();
            String value = resolveField(fieldKey, model);
            if (value != null) {
                resolved.put(token, value);
            }
        }
        return resolved;
    }

    private String resolveField(String fieldKey, ResumeRenderModel model) {
        if (fieldKey == null) return null;
        return switch (fieldKey.toUpperCase(Locale.ROOT)) {
            case "NAME" -> model.fullName();
            case "EMAIL" -> model.email();
            case "PHONE" -> model.phone();
            case "HEADLINE" -> model.headline();
            case "SUMMARY" -> model.summary();
            case "LINKS" -> model.links() == null ? null : String.join(", ", model.links());
            case "SKILLS" -> model.skills() == null ? null : String.join(", ", model.skills());
            case "EXPERIENCE" -> formatExperience(model.experience());
            case "EDUCATION" -> formatEducation(model.education());
            case "PROJECTS" -> formatProjects(model.projects());
            case "CERTIFICATIONS" -> formatCertifications(model.certifications());
            case "ACHIEVEMENTS" -> formatAchievements(model.achievements());
            default -> null;
        };
    }

    // Plain-text formatting with a literal bullet character — not real DOCX list numbering
    // (w:numPr), which would need generating/merging a numbering part; see DocxMailMerge's own
    // class comment and this feature's documented limitation on exact list-style preservation.

    private String formatExperience(List<ExperienceEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ExperienceEntry e : entries) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(joinNonBlank(" — ", e.title(), e.company()));
            if (e.dateRange() != null && !e.dateRange().isBlank()) sb.append(" (").append(e.dateRange()).append(")");
            for (String bullet : e.bullets()) {
                sb.append("\n• ").append(bullet);
            }
        }
        return sb.toString();
    }

    private String formatEducation(List<EducationEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (EducationEntry e : entries) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(joinNonBlank(", ", e.degree(), e.field()));
            if (e.institution() != null && !e.institution().isBlank()) sb.append(" — ").append(e.institution());
            if (e.dateRange() != null && !e.dateRange().isBlank()) sb.append(" (").append(e.dateRange()).append(")");
        }
        return sb.toString();
    }

    private String formatProjects(List<ProjectEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ProjectEntry p : entries) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(p.name());
            if (p.description() != null && !p.description().isBlank()) sb.append("\n").append(p.description());
        }
        return sb.toString();
    }

    private String formatCertifications(List<CertificationEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (CertificationEntry c : entries) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(joinNonBlank(" — ", c.name(), c.issuer()));
        }
        return sb.toString();
    }

    private String formatAchievements(List<ResumeRenderModel.AchievementEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ResumeRenderModel.AchievementEntry a : entries) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("• ").append(a.title());
        }
        return sb.toString();
    }

    private static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(part);
        }
        return sb.toString();
    }

    // ---- upstream fetches, mirroring DocumentRenderService's identical pattern -------------

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

    // ---- validation / helpers ---------------------------------------------------------------

    /** Validates size/filename, then the format itself — checking *both* the extension and the
     *  file's own magic-byte signature, never the extension alone (point 1 of the feature spec):
     *  a {@code .pdf}-named file that isn't really a PDF, or a {@code .docx}-named file that
     *  isn't really a ZIP/OOXML package, is rejected here before either analyzer ever sees it.
     *  DOCX is a ZIP archive — every valid one starts with the {@code "PK"} local-file-header
     *  signature; a PDF starts with the literal {@code "%PDF-"} header — both cheap, well-known
     *  checks that reject an obviously-wrong or malicious file before the heavier real parse
     *  ({@code docx4j}/PDFBox, in {@code storeAndAnalyze}) is even attempted. */
    private TemplateFormat detectFormat(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file is empty.");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file exceeds the 5 MB limit.");
        }
        String filename = originalFilename == null ? "" : originalFilename.trim();
        if (filename.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The uploaded file has no filename.");
        }
        if (filename.length() > 200) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The filename is too long.");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("\0") || filename.contains("..")) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The filename contains invalid characters.");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        boolean claimsDocx = lower.endsWith(".docx");
        boolean claimsPdf = lower.endsWith(".pdf");
        if (!claimsDocx && !claimsPdf) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "Only Word (.docx) or PDF (.pdf) files are supported.");
        }

        if (claimsDocx) {
            boolean looksLikeZip = bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4B; // "PK"
            if (!looksLikeZip) {
                throw new ApiException(ErrorCode.FILE_REJECTED, "The file does not look like a valid .docx document.");
            }
            return TemplateFormat.DOCX;
        }

        boolean looksLikePdf = bytes.length >= 5
                && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        if (!looksLikePdf) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The file does not look like a valid PDF document.");
        }
        return TemplateFormat.PDF;
    }

    /** Display-only — never used to derive the storage key (that's always a random UUID, see
     *  ObjectStorageService). Strips anything that survived {@link #validateUpload} being
     *  stricter than this defensively needs to be. */
    private static String sanitizeFilename(String filename) {
        String trimmed = filename.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
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
