package ai.careerforge.document.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * docs/DATABASE.md &sect;3, ADR-002. One rendered artifact per (resumeVersionId, format) —
 * re-rendering (e.g. a different template) replaces the row and the stored object rather
 * than accumulating history, matching the resume-content generation pattern of overwriting
 * rather than appending for this milestone slice.
 *
 * <p>{@code objectKey} is a random UUID path, never derived from user input or guessable —
 * the bucket itself is private (no anonymous read), and the only way to a byte is this
 * service's own authenticated download endpoint.
 */
@Document(collection = "rendered_documents")
public class RenderedDocument {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("resumeVersionId")
    private String resumeVersionId;

    @Field("documentType")
    private DocumentType documentType;

    @Field("format")
    private DocumentFormat format;

    @Field("objectKey")
    private String objectKey;

    @Field("bucket")
    private String bucket;

    @Field("sha256")
    private String sha256;

    @Field("byteSize")
    private long byteSize;

    @Field("pageCount")
    private int pageCount;

    @Field("templateId")
    private String templateId;

    @Field("templateVersion")
    private String templateVersion;

    @Field("renderEngineVersion")
    private String renderEngineVersion;

    @CreatedDate
    @Field("renderedAt")
    private Instant renderedAt;

    protected RenderedDocument() {
        // Spring Data
    }

    public RenderedDocument(String userId, String resumeVersionId, DocumentType documentType, DocumentFormat format,
                            String objectKey, String bucket, String sha256, long byteSize, int pageCount,
                            String templateId, String templateVersion, String renderEngineVersion) {
        this.userId = userId;
        this.resumeVersionId = resumeVersionId;
        this.documentType = documentType;
        this.format = format;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.pageCount = pageCount;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.renderEngineVersion = renderEngineVersion;
    }

    /** Replaces this row's artifact metadata in place — used when re-rendering (e.g. a
     *  different template) overwrites the previous PDF for the same resume version. */
    public void replaceWith(String objectKey, String bucket, String sha256, long byteSize, int pageCount,
                            String templateId, String templateVersion, String renderEngineVersion) {
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.pageCount = pageCount;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.renderEngineVersion = renderEngineVersion;
        this.renderedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String resumeVersionId() {
        return resumeVersionId;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public DocumentFormat format() {
        return format;
    }

    public String objectKey() {
        return objectKey;
    }

    public String bucket() {
        return bucket;
    }

    public String sha256() {
        return sha256;
    }

    public long byteSize() {
        return byteSize;
    }

    public int pageCount() {
        return pageCount;
    }

    public String templateId() {
        return templateId;
    }

    public String templateVersion() {
        return templateVersion;
    }

    public String renderEngineVersion() {
        return renderEngineVersion;
    }

    public Instant renderedAt() {
        return renderedAt;
    }
}
