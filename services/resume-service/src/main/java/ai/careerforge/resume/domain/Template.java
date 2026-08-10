package ai.careerforge.resume.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Selectable template *metadata* (docs/DATABASE.md &sect;3 "templates", ADR-004). The
 * renderable asset — HTML/CSS or DOCX that document-service would use to actually produce a
 * file — does not exist yet (document-service has no rendering code), so this collection is
 * deliberately catalogue-only: id, display info, and enough structure for the frontend to
 * render an honest preview and for generation to record which template was used.
 *
 * <p>{@code id} is a stable human-readable slug (e.g. {@code "classic"}) rather than a
 * generated ObjectId, matching the {@code templateId} examples already named in
 * docs/DATABASE.md and giving {@code MongoRepository#save} natural upsert semantics for the
 * seeder.
 */
@Document(collection = "templates")
public class Template {

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    /** Key the frontend maps to a real local preview component — not an image asset key, since
     *  no thumbnail-rendering pipeline exists (see ADR-016). */
    @Field("previewKey")
    private String previewKey;

    @Field("type")
    private TemplateType type;

    @Field("version")
    private String version;

    @Field("status")
    private TemplateStatus status;

    @Field("source")
    private TemplateSource source;

    /** Null for BUILT_IN and ONLINE. Set for CUSTOM_UPLOAD once upload ships — reserved now so
     *  ownership checks have somewhere to read from without a schema change later. */
    @Field("ownerUserId")
    private String ownerUserId;

    /** Formats document-service will render once it exists to render them at all. */
    @Field("supportedFormats")
    private List<String> supportedFormats;

    @Field("atsSafe")
    private boolean atsSafe;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected Template() {
        // Spring Data
    }

    public Template(String id, String name, String description, String previewKey, TemplateType type,
                    String version, TemplateStatus status, TemplateSource source, String ownerUserId,
                    List<String> supportedFormats, boolean atsSafe) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.previewKey = previewKey;
        this.type = type;
        this.version = version;
        this.status = status;
        this.source = source;
        this.ownerUserId = ownerUserId;
        this.supportedFormats = supportedFormats;
        this.atsSafe = atsSafe;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String previewKey() {
        return previewKey;
    }

    public TemplateType type() {
        return type;
    }

    public String version() {
        return version;
    }

    public TemplateStatus status() {
        return status;
    }

    public TemplateSource source() {
        return source;
    }

    public String ownerUserId() {
        return ownerUserId;
    }

    public List<String> supportedFormats() {
        return supportedFormats;
    }

    public boolean atsSafe() {
        return atsSafe;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == TemplateStatus.ACTIVE;
    }

    /** True if {@code userId} is allowed to select this template for generation. Built-in and
     *  online templates are selectable by anyone; an uploaded template only by its owner. */
    public boolean isSelectableBy(String userId) {
        return source != TemplateSource.CUSTOM_UPLOAD || (ownerUserId != null && ownerUserId.equals(userId));
    }
}
