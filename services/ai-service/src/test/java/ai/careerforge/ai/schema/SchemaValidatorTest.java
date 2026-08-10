package ai.careerforge.ai.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Schema validation is the last line of defence against prompt injection: a model that
 * complied with an injected instruction produces prose, and prose is not the contract.
 */
class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator(new ObjectMapper());

    @Test
    void acceptsWellFormedAnalysis() {
        String json = """
                {"isJobPosting":true,"jobTitle":"Backend Engineer","company":"Acme",
                 "requirements":[{"requirementId":"REQ-001","text":"Java 21",
                 "type":"HARD_REQUIRED","weight":5,"normalisedTerms":["java"]}],
                 "keywords":["java","spring"]}""";

        assertThat(validator.validate(json, "jd-analysis.schema.json")
                .path("jobTitle").asText()).isEqualTo("Backend Engineer");
    }

    @Test
    void unwrapsMarkdownFencesSomeModelsAddAnyway() {
        String json = """
                ```json
                {"isJobPosting":false,"requirements":[],"keywords":[]}
                ```""";

        assertThat(validator.validate(json, "jd-analysis.schema.json")
                .path("isJobPosting").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("rejects a successful injection, which returns prose rather than an object")
    void rejectsProse() {
        assertThatThrownBy(() -> validator.validate(
                "I cannot comply with that request.", "jd-analysis.schema.json"))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void rejectsAnInvalidRequirementType() {
        String json = """
                {"isJobPosting":true,"requirements":[{"requirementId":"REQ-001",
                 "text":"Java","type":"MADE_UP","weight":3}],"keywords":[]}""";

        assertThatThrownBy(() -> validator.validate(json, "jd-analysis.schema.json"))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void rejectsMalformedEvidenceIds() {
        String json = """
                {"matches":[{"requirementId":"REQ-001","evidenceIds":["not-an-id"],
                 "matchStrength":"STRONG"}]}""";

        assertThatThrownBy(() -> validator.validate(json, "evidence-selection.schema.json"))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    @DisplayName("rejects resume content whose statements cite nothing")
    void rejectsUngroundedResumeContent() {
        String json = """
                {"summary":{"text":"Motivated engineer.","evidenceIds":[]},
                 "experienceBullets":[]}""";

        assertThatThrownBy(() -> validator.validate(json, "resume-content.schema.json"))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void errorNeverContainsTheOffendingContent() {
        assertThatThrownBy(() -> validator.validate(
                "Confidential salary is 200000", "jd-analysis.schema.json"))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageNotContaining("200000");
    }
}
