package ai.careerforge.jd.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** One per JD version, cached after the first ai-service call (docs/DATABASE.md &sect;3). */
@Document(collection = "jd_analyses")
public class JdAnalysis {

    @Id
    private String id;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("jdVersionId")
    private String jdVersionId;

    @Field("title")
    private String title;

    @Field("company")
    private String company;

    @Field("seniority")
    private String seniority;

    @Field("keywords")
    private List<String> keywords = List.of();

    @Field("requirements")
    private List<Requirement> requirements = List.of();

    @Field("promptVersion")
    private String promptVersion;

    @Field("modelId")
    private String modelId;

    @CreatedDate
    @Field("analysedAt")
    private Instant analysedAt;

    protected JdAnalysis() {
        // Spring Data
    }

    public JdAnalysis(String jobDescriptionId, String jdVersionId, String title, String company,
                      String seniority, List<String> keywords, List<Requirement> requirements,
                      String promptVersion, String modelId) {
        this.jobDescriptionId = jobDescriptionId;
        this.jdVersionId = jdVersionId;
        this.title = title;
        this.company = company;
        this.seniority = seniority;
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.requirements = requirements == null ? List.of() : List.copyOf(requirements);
        this.promptVersion = promptVersion;
        this.modelId = modelId;
    }

    public String id() {
        return id;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public String jdVersionId() {
        return jdVersionId;
    }

    public String title() {
        return title;
    }

    public String company() {
        return company;
    }

    public String seniority() {
        return seniority;
    }

    public List<String> keywords() {
        return keywords;
    }

    public List<Requirement> requirements() {
        return requirements;
    }
}
