package ai.careerforge.render.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO validation and Jackson round-trip coverage for {@link ResumeRenderRequest} — the
 *  render-service side of ADR-036's content/render boundary contract. */
class ResumeRenderRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                "San Francisco, CA", List.of(new HeaderLink("LinkedIn", "https://linkedin.com/in/priyasharma")));
    }

    private static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    private static ResumeRenderRequest validRequest() {
        SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", "Remote",
                "2022-01", "Present",
                List.of(new ContentLeaf("Built and shipped a payments service.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE)));

        return new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(),
                new ContentLeaf("Backend engineer with 6 years of experience.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE),
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());
    }

    @Test
    @DisplayName("a valid request has no Jakarta violations")
    void validRequestHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("Jackson round-trip is exact")
    void roundTripsThroughJackson() throws Exception {
        ResumeRenderRequest original = validRequest();

        String json = mapper.writeValueAsString(original);
        ResumeRenderRequest restored = mapper.readValue(json, ResumeRenderRequest.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("a missing schemaVersion is rejected")
    void missingSchemaVersionIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(null, base.template(), base.outputFormat(),
                base.header(), base.summary(), base.sections(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a non-semver schemaVersion is rejected")
    void nonSemverSchemaVersionIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest("v1", base.template(), base.outputFormat(),
                base.header(), base.summary(), base.sections(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing template identifier is rejected")
    void missingTemplateIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), null, base.outputFormat(),
                base.header(), base.summary(), base.sections(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing output format is rejected")
    void missingOutputFormatIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), base.template(), null,
                base.header(), base.summary(), base.sections(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing header is rejected")
    void missingHeaderIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), null, base.summary(), base.sections(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("no sections at all is rejected")
    void noSectionsIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.summary(), List.of(), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a malformed evidenceId is rejected")
    void malformedEvidenceIdIsRejected() {
        SectionEntry entry = new SectionEntry("NOT-A-REAL-ID", "Software Engineer", "Acme Corp",
                null, null, null, List.of());
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.summary(),
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), base.renderHints());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing renderHints is rejected")
    void missingRenderHintsIsRejected() {
        ResumeRenderRequest base = validRequest();
        ResumeRenderRequest request = new ResumeRenderRequest(base.schemaVersion(), base.template(),
                base.outputFormat(), base.header(), base.summary(), base.sections(), null);

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
