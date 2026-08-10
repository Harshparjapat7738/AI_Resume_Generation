package ai.careerforge.jd.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Immutable — an edit creates a new version so a confirmation always points at exact bytes. */
@Document(collection = "jd_versions")
public class JdVersion {

    @Id
    private String id;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("version")
    private int version;

    @Field("rawText")
    private String rawText;

    @Field("normalisedText")
    private String normalisedText;

    @Field("extractionMethod")
    private String extractionMethod;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    protected JdVersion() {
        // Spring Data
    }

    public JdVersion(String jobDescriptionId, int version, String rawText, String normalisedText,
                     String extractionMethod) {
        this.jobDescriptionId = jobDescriptionId;
        this.version = version;
        this.rawText = rawText;
        this.normalisedText = normalisedText;
        this.extractionMethod = extractionMethod;
    }

    public String id() {
        return id;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public int version() {
        return version;
    }

    public String rawText() {
        return rawText;
    }

    public String normalisedText() {
        return normalisedText;
    }
}
