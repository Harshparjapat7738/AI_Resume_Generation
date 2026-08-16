package ai.careerforge.profile.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.profile.domain.Template;
import ai.careerforge.profile.domain.TemplateDocumentType;
import ai.careerforge.profile.domain.TemplateFileType;
import ai.careerforge.profile.repository.TemplateRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * The "My Templates" library (ADR-034): a user's own Resume/Cover Letter files, uploaded once
 * and reused across every JD-optimization handoff instead of being re-uploaded per generation.
 *
 * <p>Deliberately does nothing beyond validate, store and catalogue the raw bytes — no
 * structural analysis, no placeholder detection, no mail-merge, no AI call of any kind. The
 * uploaded file is never altered; what a user downloads back is byte-for-byte what they
 * uploaded. This is the one thing the now-deleted {@code document-service}'s custom-template
 * pipeline conflated with real document generation — ADR-033 removed generation entirely, and
 * this service does not reintroduce any part of it.
 */
@Service
public class TemplateService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    /** Mirrors {@code spring.servlet.multipart.max-file-size} (application.yml, MAX_UPLOAD_SIZE,
     *  default 5MB) — enforced again here so this service rejects an oversized file with the
     *  same clear reason regardless of caller. */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_FILENAME_LENGTH = 200;

    private final TemplateRepository templates;
    private final ObjectStorageService storage;

    public TemplateService(TemplateRepository templates, ObjectStorageService storage) {
        this.templates = templates;
        this.storage = storage;
    }

    public List<Template> list(String userId) {
        return templates.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 404, not 403, for a template that exists but belongs to someone else (BOLA hardening —
     *  same rule as every other user-scoped resource in this platform). */
    public Template requireOwned(String userId, String id) {
        return templates.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    public Template upload(String userId, byte[] bytes, String originalFilename, String name,
                           TemplateDocumentType documentType) {
        TemplateFileType fileType = detectFileType(bytes, originalFilename);
        String displayName = normalizeName(name, originalFilename);
        String sha256 = sha256Hex(bytes);

        if (templates.existsByUserIdAndSha256(userId, sha256)) {
            throw new ApiException(ErrorCode.FILE_REJECTED,
                    "You've already uploaded this exact file. Rename or delete the existing "
                            + "template first if you want to replace it.");
        }

        String contentType = fileType == TemplateFileType.PDF ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        String objectKey = storage.upload(bytes, contentType);

        // The very first template a user ever saves becomes their default automatically —
        // otherwise "no saved templates yet" would still show a generation page with nothing
        // pre-selected even after the user's one and only upload.
        boolean makeDefault = templates.countByUserId(userId) == 0;

        Template template = new Template(userId, displayName, sanitizeFilename(originalFilename), fileType,
                documentType == null ? TemplateDocumentType.RESUME : documentType, objectKey, storage.bucket(),
                bytes.length, sha256, makeDefault);
        return templates.save(template);
    }

    public Template rename(String userId, String id, String name) {
        Template template = requireOwned(userId, id);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Template name cannot be empty.");
        }
        template.rename(trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed);
        return templates.save(template);
    }

    /** Setting a new default unsets whichever template (if any) previously held it — never more
     *  than one default per user at a time. */
    public Template setDefault(String userId, String id) {
        Template template = requireOwned(userId, id);
        templates.findByUserIdAndIsDefaultTrue(userId)
                .filter(current -> !current.id().equals(template.id()))
                .ifPresent(current -> {
                    current.markDefault(false);
                    templates.save(current);
                });
        template.markDefault(true);
        return templates.save(template);
    }

    /** Removes the stored file and its metadata row together — there is never an orphaned
     *  metadata row pointing at a deleted object, or vice versa (the storage delete is
     *  best-effort on failure, see {@link ObjectStorageService#delete}, but the metadata row
     *  is always removed, which is what the user actually observes). If the deleted template
     *  was the default, no other template automatically becomes default — an explicit choice
     *  beats a silent, possibly-surprising promotion. */
    public void delete(String userId, String id) {
        Template template = requireOwned(userId, id);
        storage.delete(template.objectKey());
        templates.delete(template);
    }

    public byte[] download(String userId, String id) {
        Template template = requireOwned(userId, id);
        return storage.download(template.objectKey());
    }

    public String contentTypeFor(Template template) {
        return template.fileType() == TemplateFileType.PDF ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
    }

    // ---- upload validation ----------------------------------------------------------------

    /** Extension AND magic-byte signature must agree — a mislabelled or corrupt file is
     *  rejected either way, never silently accepted on extension alone. No deeper structural
     *  parsing happens beyond this (task requirement: no AI/structural template analysis). */
    private TemplateFileType detectFileType(byte[] bytes, String originalFilename) {
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
        if (filename.length() > MAX_FILENAME_LENGTH) {
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
            return TemplateFileType.DOCX;
        }

        boolean looksLikePdf = bytes.length >= 5
                && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        if (!looksLikePdf) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The file does not look like a valid PDF document.");
        }
        return TemplateFileType.PDF;
    }

    /** Falls back to the original filename (minus its extension) when no display name was
     *  supplied, so "Add Template" never hard-requires a second field the user has to think
     *  of on the spot. */
    private String normalizeName(String name, String originalFilename) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty()) {
            return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        }
        String base = originalFilename == null ? "Template" : originalFilename.trim();
        int dot = base.lastIndexOf('.');
        String withoutExtension = dot > 0 ? base.substring(0, dot) : base;
        return withoutExtension.length() > MAX_NAME_LENGTH
                ? withoutExtension.substring(0, MAX_NAME_LENGTH) : withoutExtension;
    }

    /** Display-only — never used to derive the storage key (that's always a random UUID, see
     *  ObjectStorageService). */
    private static String sanitizeFilename(String filename) {
        String trimmed = filename.trim();
        return trimmed.length() > MAX_FILENAME_LENGTH ? trimmed.substring(0, MAX_FILENAME_LENGTH) : trimmed;
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
