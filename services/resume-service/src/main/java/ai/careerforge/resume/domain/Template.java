package ai.careerforge.resume.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Selectable template *metadata* (docs/DATABASE.md &sect;3 "templates", ADR-004). The
 * renderable asset — HTML/CSS for a built-in template, or the raw DOCX + extracted
 * structure/placeholders for a custom-uploaded one — lives in document-service; this
 * collection is the catalogue a user actually browses and picks from, plus (for
 * {@code CUSTOM_UPLOAD} rows only) the display/mapping fields specific to that upload. See
 * {@code CustomTemplateAsset} in document-service for the asset half of this split, and
 * {@code TemplateService#uploadCustom} for how the two get created together.
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
     *  no thumbnail-rendering pipeline exists (see ADR-016). Null for custom uploads — those
     *  render their own structural summary instead (see {@code structure}). */
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

    /** Null for BUILT_IN and ONLINE. Set for CUSTOM_UPLOAD. */
    @Field("ownerUserId")
    private String ownerUserId;

    /** Formats document-service can render for this template. Built-ins: PDF (and DOCX once
     *  the generic pipeline covers them too). Custom uploads: whichever single format the
     *  owner actually uploaded, DOCX or PDF (ADR-023) — the output format always matches the
     *  source template's own format, never converted between the two. */
    @Field("supportedFormats")
    private List<String> supportedFormats;

    @Field("atsSafe")
    private boolean atsSafe;

    // ---- CUSTOM_UPLOAD-only fields (null/empty for BUILT_IN and ONLINE) --------------------

    @Field("originalFilename")
    private String originalFilename;

    /** Raw structural facts as document-service's analyzer returned them (page size, margins,
     *  fonts, colors, headings, table/column counts, header/footer presence) — passed straight
     *  through for display; resume-service never interprets these values itself. */
    @Field("structure")
    private Map<String, Object> structure;

    /** Every {@code {{token}}} placeholder document-service's analyzer found, each with a
     *  short surrounding-text excerpt and (if the token matched a known synonym) a suggested
     *  profile field — same pass-through-for-display approach as {@code structure}. */
    @Field("detectedFields")
    private List<Map<String, Object>> detectedFields;

    /** token -> profile field key (see document-service's {@code ProfileFieldCatalog}, e.g.
     *  {@code "NAME"}, {@code "EXPERIENCE"}). Starts as the analyzer's suggestions, editable by
     *  the owner at any time via {@link #updateMapping}. */
    @Field("fieldMappings")
    private Map<String, String> fieldMappings;

    /** This owner's default template for {@code type}, shown/pre-selected first. At most one
     *  custom template per (owner, type) may be true — enforced in {@code TemplateService}. */
    @Field("isDefault")
    private boolean isDefault;

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

    /** For a newly-uploaded custom template — id is the shared id document-service's asset
     *  record was created with (see {@code TemplateService#uploadCustom}). {@code format} is
     *  document-service's own real answer ({@code "DOCX"} or {@code "PDF"}, ADR-023) — never
     *  assumed, since a custom upload is exactly as likely to be either now. */
    public static Template forCustomUpload(String id, String userId, String name, TemplateType type,
                                            String originalFilename, String format, Map<String, Object> structure,
                                            List<Map<String, Object>> detectedFields, Map<String, String> suggestedMapping) {
        Template template = new Template(id, name, "Custom template uploaded by you.", null, type, "1",
                TemplateStatus.ACTIVE, TemplateSource.CUSTOM_UPLOAD, userId,
                List.of(format == null ? "DOCX" : format), false);
        template.originalFilename = originalFilename;
        template.structure = structure;
        template.detectedFields = detectedFields;
        template.fieldMappings = suggestedMapping;
        template.isDefault = false;
        return template;
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

    public String originalFilename() {
        return originalFilename;
    }

    public Map<String, Object> structure() {
        return structure;
    }

    public List<Map<String, Object>> detectedFields() {
        return detectedFields;
    }

    public Map<String, String> fieldMappings() {
        return fieldMappings;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == TemplateStatus.ACTIVE;
    }

    public boolean isCustom() {
        return source == TemplateSource.CUSTOM_UPLOAD;
    }

    /** True if {@code userId} is allowed to select this template for generation. Built-in and
     *  online templates are selectable by anyone; an uploaded template only by its owner. */
    public boolean isSelectableBy(String userId) {
        return source != TemplateSource.CUSTOM_UPLOAD || (ownerUserId != null && ownerUserId.equals(userId));
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void updateMapping(Map<String, String> mappings) {
        this.fieldMappings = mappings;
    }

    public void setDefault(boolean value) {
        this.isDefault = value;
    }
}
