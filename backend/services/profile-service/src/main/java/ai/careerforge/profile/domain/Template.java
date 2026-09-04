package ai.careerforge.profile.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A user's own uploaded Resume/Cover Letter template — the "My Templates" library. One row per
 * upload, many per user (unlike {@link Profile}, which is one per user).
 *
 * <p>This is deliberately a plain file record, not a renderable asset: no structure analysis, no
 * detected fields, no mail-merge mapping, no AI involvement of any kind. The uploaded bytes are
 * never altered, parsed for placeholders, or rendered into — they are stored exactly as
 * uploaded and handed back exactly as stored, either as a raw download or as the file the user
 * carries into an external tool (ChatGPT, Gemini, Word, …) alongside their JD-optimization data.
 * The now-deleted {@code document-service}'s custom-template pipeline (structural analysis,
 * mail-merge, PDF/DOCX rendering) is not reintroduced here in any form (ADR-033).
 *
 * <p>{@code isDefault} is a single flag per user, not scoped per {@link TemplateDocumentType} —
 * the product no longer has separate resume-generation and cover-letter-generation flows to pair
 * a type-scoped default with (ADR-033 removed both), so "my usual template regardless of type" is
 * the simpler, more honest model. {@link #objectKey()}/{@link #bucket()} are internal storage
 * coordinates and are never serialised over HTTP (see {@code api/dto/TemplateResponses}).
 */
@Document(collection = "templates")
public class Template {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("name")
    private String name;

    @Field("originalFilename")
    private String originalFilename;

    @Field("fileType")
    private TemplateFileType fileType;

    @Field("documentType")
    private TemplateDocumentType documentType;

    @Field("objectKey")
    private String objectKey;

    @Field("bucket")
    private String bucket;

    @Field("byteSize")
    private long byteSize;

    @Field("sha256")
    private String sha256;

    @Field("isDefault")
    private boolean isDefault;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    protected Template() {
        // Spring Data
    }

    public Template(String userId, String name, String originalFilename, TemplateFileType fileType,
                     TemplateDocumentType documentType, String objectKey, String bucket, long byteSize,
                     String sha256, boolean isDefault) {
        this.userId = userId;
        this.name = name;
        this.originalFilename = originalFilename;
        this.fileType = fileType;
        this.documentType = documentType;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.isDefault = isDefault;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public TemplateFileType fileType() {
        return fileType;
    }

    public TemplateDocumentType documentType() {
        return documentType;
    }

    public String objectKey() {
        return objectKey;
    }

    public String bucket() {
        return bucket;
    }

    public long byteSize() {
        return byteSize;
    }

    public String sha256() {
        return sha256;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void markDefault(boolean value) {
        this.isDefault = value;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
