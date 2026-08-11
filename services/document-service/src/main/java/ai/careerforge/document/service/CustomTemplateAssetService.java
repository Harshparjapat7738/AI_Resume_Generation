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
import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.DocumentType;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.render.ResumeRenderModel;
import ai.careerforge.document.render.ResumeRenderModel.CertificationEntry;
import ai.careerforge.document.render.ResumeRenderModel.EducationEntry;
import ai.careerforge.document.render.ResumeRenderModel.ExperienceEntry;
import ai.careerforge.document.render.ResumeRenderModel.ProjectEntry;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
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
    private static final String MAIL_MERGE_ENGINE_VERSION = "docx4j-11.5.3+docx-mailmerge-1";
    /** Mirrors {@code spring.servlet.multipart.max-file-size} (application.yml,
     *  {@code MAX_UPLOAD_SIZE}, default 5MB) — enforced again here so this service rejects an
     *  oversized file with the same clear reason regardless of caller. */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final DocxStructureAnalyzer analyzer;
    private final DocxMailMerge mailMerge;
    private final ObjectStorageService storage;
    private final CustomTemplateAssetRepository assets;
    private final RenderedDocumentRepository documents;
    private final ResumeServiceClient resumeServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final ResumeRenderModelBuilder modelBuilder;

    public CustomTemplateAssetService(DocxStructureAnalyzer analyzer, DocxMailMerge mailMerge,
                                       ObjectStorageService storage, CustomTemplateAssetRepository assets,
                                       RenderedDocumentRepository documents, ResumeServiceClient resumeServiceClient,
                                       ProfileServiceClient profileServiceClient, ResumeRenderModelBuilder modelBuilder) {
        this.analyzer = analyzer;
        this.mailMerge = mailMerge;
        this.storage = storage;
        this.assets = assets;
        this.documents = documents;
        this.resumeServiceClient = resumeServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.modelBuilder = modelBuilder;
    }

    public CustomTemplateAsset storeAndAnalyze(String userId, byte[] bytes, String originalFilename) {
        validateUpload(bytes, originalFilename);
        // If this can't be parsed as a real WordprocessingML package, DocxStructureAnalyzer.load
        // itself throws FILE_REJECTED — a corrupt, malicious, or merely-mislabelled file never
        // reaches storage.
        WordprocessingMLPackage pkg = analyzer.load(bytes);
        DocxStructureAnalyzer.Analysis analysis = analyzer.analyze(pkg);

        String sha256 = sha256Hex(bytes);
        String objectKey = storage.upload(bytes, DOCX_CONTENT_TYPE);
        CustomTemplateAsset asset = new CustomTemplateAsset(
                userId, sanitizeFilename(originalFilename), objectKey, bytes.length, sha256,
                analysis.structure(), analysis.detectedFields());
        return assets.save(asset);
    }

    public CustomTemplateAsset requireOwned(String userId, String id) {
        return assets.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    public CustomTemplateAsset duplicate(String userId, String id) {
        CustomTemplateAsset original = requireOwned(userId, id);
        byte[] bytes = storage.download(original.objectKey());
        String newObjectKey = storage.upload(bytes, DOCX_CONTENT_TYPE);
        CustomTemplateAsset copy = new CustomTemplateAsset(
                userId, original.originalFilename(), newObjectKey, original.byteSize(), original.sha256(),
                original.structure(), original.detectedFields());
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

    public RenderedDocument generate(String userId, String assetId, String resumeVersionId, Map<String, String> fieldMappings) {
        CustomTemplateAsset asset = requireOwned(userId, assetId);
        byte[] originalBytes = storage.download(asset.objectKey());
        WordprocessingMLPackage pkg = analyzer.load(originalBytes);

        ResumeVersionDto resume = fetchOwnedResume(resumeVersionId);
        ProfileDto profile = fetchProfile();
        ResumeRenderModel model = modelBuilder.build(resume, profile);
        Map<String, String> resolved = resolveValues(model, fieldMappings == null ? Map.of() : fieldMappings);

        byte[] merged = mailMerge.merge(pkg, resolved);
        String sha256 = sha256Hex(merged);

        Optional<RenderedDocument> existing =
                documents.findByResumeVersionIdAndFormatAndUserId(resumeVersionId, DocumentFormat.DOCX, userId);
        if (existing.isPresent() && sha256.equals(existing.get().sha256()) && assetId.equals(existing.get().templateId())) {
            return existing.get();
        }

        String objectKey = storage.upload(merged, DOCX_CONTENT_TYPE);
        if (existing.isPresent()) {
            RenderedDocument doc = existing.get();
            String previousObjectKey = doc.objectKey();
            // pageCount: not computed for DOCX (no reliable page count without rendering it —
            // unlike the PDF pipeline, which counts real pages via PDFBox); 0 is a deliberate
            // "not applicable" sentinel the frontend treats as "unknown" for this format.
            doc.replaceWith(objectKey, storage.bucket(), sha256, merged.length, 0, assetId, "custom", MAIL_MERGE_ENGINE_VERSION);
            RenderedDocument saved = documents.save(doc);
            storage.delete(previousObjectKey);
            return saved;
        }

        RenderedDocument doc = new RenderedDocument(userId, resumeVersionId, DocumentType.RESUME, DocumentFormat.DOCX,
                objectKey, storage.bucket(), sha256, merged.length, 0, assetId, "custom", MAIL_MERGE_ENGINE_VERSION);
        return documents.save(doc);
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

    private void validateUpload(byte[] bytes, String originalFilename) {
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
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "Only Word (.docx) files are supported today.");
        }
        // DOCX is a ZIP archive — every valid one starts with the "PK" local-file-header
        // signature. A cheap, early rejection for anything that isn't even a ZIP before
        // handing it to docx4j at all.
        if (bytes.length < 2 || bytes[0] != 0x50 || bytes[1] != 0x4B) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The file does not look like a valid .docx document.");
        }
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
