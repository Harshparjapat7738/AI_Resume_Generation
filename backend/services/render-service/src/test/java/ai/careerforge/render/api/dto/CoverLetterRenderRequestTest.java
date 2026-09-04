package ai.careerforge.render.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO validation and Jackson round-trip coverage for {@link CoverLetterRenderRequest}. */
class CoverLetterRenderRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", null, null, List.of());
    }

    private static CoverLetterRenderRequest validRequest() {
        List<ContentLeaf> paragraphs = List.of(
                new ContentLeaf("I'm writing to apply for the Senior Backend Engineer role.",
                        List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE));

        return new CoverLetterRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(),
                "Senior Backend Engineer", "Acme Corp", "Dear Hiring Manager,", paragraphs, "Sincerely,",
                "Priya Sharma", new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null));
    }

    @Test
    @DisplayName("a valid request has no Jakarta violations")
    void validRequestHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("Jackson round-trip is exact")
    void roundTripsThroughJackson() throws Exception {
        CoverLetterRenderRequest original = validRequest();

        String json = mapper.writeValueAsString(original);
        CoverLetterRenderRequest restored = mapper.readValue(json, CoverLetterRenderRequest.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("no paragraphs at all is rejected")
    void noParagraphsIsRejected() {
        CoverLetterRenderRequest base = validRequest();
        CoverLetterRenderRequest request = new CoverLetterRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.targetRole(), base.targetCompany(), base.salutation(),
                List.of(), base.closing(), base.signatureName(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a blank salutation is rejected")
    void blankSalutationIsRejected() {
        CoverLetterRenderRequest base = validRequest();
        CoverLetterRenderRequest request = new CoverLetterRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.targetRole(), base.targetCompany(), " ",
                base.paragraphs(), base.closing(), base.signatureName(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing signatureName is rejected")
    void missingSignatureNameIsRejected() {
        CoverLetterRenderRequest base = validRequest();
        CoverLetterRenderRequest request = new CoverLetterRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.targetRole(), base.targetCompany(), base.salutation(),
                base.paragraphs(), base.closing(), null, base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("targetRole and targetCompany may both be absent without violation")
    void targetRoleAndCompanyAreOptional() {
        CoverLetterRenderRequest base = validRequest();
        CoverLetterRenderRequest request = new CoverLetterRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), null, null, base.salutation(),
                base.paragraphs(), base.closing(), base.signatureName(), base.renderHints());

        assertThat(validator.validate(request)).isEmpty();
    }
}
