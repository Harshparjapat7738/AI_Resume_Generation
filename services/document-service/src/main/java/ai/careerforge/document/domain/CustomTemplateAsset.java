package ai.careerforge.document.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The renderable side of a user-uploaded custom template — the raw DOCX bytes (in MinIO, via
 * the same {@code ObjectStorageService} the built-in PDF pipeline already uses) plus the real
 * structure/placeholders extracted from it. resume-service owns the *catalogue* entry a user
 * picks from ({@code Template} with {@code source=CUSTOM_UPLOAD}) — this is the matching
 * *asset* record, sharing the same id (minted here at upload time, then stored by resume-service
 * as that Template's id) so the two rows are found by simple id lookup, with no extra foreign
 * key or cross-service sync table needed. Mirrors the resume-service-catalogue /
 * document-service-asset split ARCHITECTURE_DECISIONS.md ADR-004/ADR-016 already established
 * for built-in templates.
 */
@Document(collection = "custom_template_assets")
public class CustomTemplateAsset {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("originalFilename")
    private String originalFilename;

    @Field("objectKey")
    private String objectKey;

    @Field("byteSize")
    private long byteSize;

    @Field("sha256")
    private String sha256;

    @Field("structure")
    private TemplateStructure structure;

    @Field("detectedFields")
    private List<DetectedField> detectedFields;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected CustomTemplateAsset() {
        // Spring Data
    }

    public CustomTemplateAsset(String userId, String originalFilename, String objectKey, long byteSize,
                                String sha256, TemplateStructure structure, List<DetectedField> detectedFields) {
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.objectKey = objectKey;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.structure = structure;
        this.detectedFields = detectedFields;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String objectKey() {
        return objectKey;
    }

    public void objectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public long byteSize() {
        return byteSize;
    }

    public String sha256() {
        return sha256;
    }

    public TemplateStructure structure() {
        return structure;
    }

    public List<DetectedField> detectedFields() {
        return detectedFields;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}
