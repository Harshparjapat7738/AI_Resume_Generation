package ai.careerforge.application.document;

import static ai.careerforge.application.document.SchemaAssertions.assertValidAgainstSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip coverage for {@link ResumeDocumentModel}: Jackson serialization, Jakarta
 * validation, and conformance to {@code schemas/resume-document-model.schema.json} — the three
 * guarantees ADR-036 relies on this record hierarchy to hold before render-service ever sees it.
 *
 * <p>Uses small, self-contained instances rather than {@code DocumentModelFixtures} — that
 * class's short/long/cover-letter fixtures exist for {@link DocumentEvidenceValidatorTest} and
 * are exercised for hostile-content round-tripping in {@code DocumentModelFixturesTest}.
 */
class ResumeDocumentModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                "San Francisco, CA", List.of(new HeaderLink("LinkedIn", "https://linkedin.com/in/priyasharma")));
    }

    private static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    private static ResumeDocumentModel validModel() {
        SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", "Remote",
                "2022-01", "Present",
                List.of(new ContentLeaf("Built and shipped a payments service handling 10,000 transactions per day.",
                        List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));
        ContentLeaf summary = new ContentLeaf("Backend engineer with 6 years of experience.",
                List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE);

        return new ResumeDocumentModel("1.0.0", header(), summary,
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))),
                renderHints(), GapReport.empty());
    }

    @Test
    @DisplayName("a valid model has no Jakarta violations")
    void validModelHasNoViolations() {
        assertThat(validator.validate(validModel())).isEmpty();
    }

    @Test
    @DisplayName("Jackson round-trip is exact")
    void roundTripsThroughJackson() throws Exception {
        ResumeDocumentModel original = validModel();

        String json = mapper.writeValueAsString(original);
        ResumeDocumentModel restored = mapper.readValue(json, ResumeDocumentModel.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("a valid model matches the JSON schema")
    void matchesSchema() {
        assertValidAgainstSchema(mapper, validModel(), "resume-document-model.schema.json");
    }

    @Test
    @DisplayName("a leaf with no evidenceId is rejected — the rule is enforced, not just documented")
    void leafWithoutEvidenceIdIsRejected() {
        ContentLeaf leaf = new ContentLeaf("Some claim.", List.of(), ContentOrigin.VERBATIM_FROM_PROFILE);

        Set<ConstraintViolation<ContentLeaf>> violations = validator.validate(leaf);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("an entry with a malformed evidenceId is rejected")
    void entryWithMalformedEvidenceIdIsRejected() {
        SectionEntry entry = new SectionEntry("NOT-A-REAL-ID", "Software Engineer", "Acme Corp",
                null, null, null, List.of());

        Set<ConstraintViolation<SectionEntry>> violations = validator.validate(entry);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("a resume with no sections is rejected")
    void resumeWithNoSectionsIsRejected() {
        ResumeDocumentModel model = new ResumeDocumentModel("1.0.0", header(), null, List.of(),
                renderHints(), GapReport.empty());

        assertThat(validator.validate(model)).isNotEmpty();
    }

    @Test
    @DisplayName("a non-semver schemaVersion is rejected")
    void nonSemverSchemaVersionIsRejected() {
        ResumeDocumentModel base = validModel();
        ResumeDocumentModel model = new ResumeDocumentModel("v1", base.header(), base.summary(),
                base.sections(), base.renderHints(), base.gapReport());

        assertThat(validator.validate(model)).isNotEmpty();
    }

    @Test
    @DisplayName("a header with no photo field is the only shape available — nothing to violate")
    void headerHasNoPhotoField() {
        // Compile-time guarantee, not a runtime assertion: DocumentHeader simply has no photo/
        // avatar/imageUrl component for a test to construct in the first place (ADR-036).
        assertThat(DocumentHeader.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("fullName", "email", "phone", "location", "links");
    }
}
