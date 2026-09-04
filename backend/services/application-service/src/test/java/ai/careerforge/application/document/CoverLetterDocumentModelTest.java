package ai.careerforge.application.document;

import static ai.careerforge.application.document.SchemaAssertions.assertValidAgainstSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Round-trip coverage for {@link CoverLetterDocumentModel} — see
 *  {@link ResumeDocumentModelTest} for the same three guarantees on the resume side. */
class CoverLetterDocumentModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", null, null, List.of());
    }

    private static CoverLetterDocumentModel validModel() {
        List<ContentLeaf> paragraphs = List.of(
                new ContentLeaf("I'm writing to apply for the Senior Backend Engineer role.",
                        List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE));

        return new CoverLetterDocumentModel("1.0.0", header(), "Senior Backend Engineer", "Acme Corp",
                "Dear Hiring Manager,", paragraphs, "Sincerely,", "Priya Sharma",
                new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null), GapReport.empty());
    }

    @Test
    @DisplayName("a valid model has no Jakarta violations")
    void validModelHasNoViolations() {
        assertThat(validator.validate(validModel())).isEmpty();
    }

    @Test
    @DisplayName("Jackson round-trip is exact")
    void roundTripsThroughJackson() throws Exception {
        CoverLetterDocumentModel original = validModel();

        String json = mapper.writeValueAsString(original);
        CoverLetterDocumentModel restored = mapper.readValue(json, CoverLetterDocumentModel.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("a valid model matches the JSON schema")
    void matchesSchema() {
        assertValidAgainstSchema(mapper, validModel(), "cover-letter-document-model.schema.json");
    }

    @Test
    @DisplayName("a cover letter with no paragraphs is rejected")
    void coverLetterWithNoParagraphsIsRejected() {
        CoverLetterDocumentModel base = validModel();
        CoverLetterDocumentModel model = new CoverLetterDocumentModel(base.schemaVersion(), base.header(),
                base.targetRole(), base.targetCompany(), base.salutation(), List.of(), base.closing(),
                base.signatureName(), base.renderHints(), base.gapReport());

        assertThat(validator.validate(model)).isNotEmpty();
    }
}
