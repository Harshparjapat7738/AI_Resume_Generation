package ai.careerforge.application.document;

import static ai.careerforge.application.document.SchemaAssertions.assertValidAgainstSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * Proves the {@link DocumentModelFixtures} fixtures are themselves valid, schema-conformant, and
 * — for the long fixture specifically — that its deliberately hostile content (HTML/script-like
 * text, non-Latin script, apostrophes, ampersands, em-dashes, one long bullet) survives a Jackson
 * round-trip byte-for-byte rather than merely "equals() happened to pass".
 */
class DocumentModelFixturesTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shortResumeIsValid() {
        assertThat(validator.validate(DocumentModelFixtures.shortResume())).isEmpty();
        assertValidAgainstSchema(mapper, DocumentModelFixtures.shortResume(), "resume-document-model.schema.json");
    }

    @Test
    void longResumeIsValid() {
        assertThat(validator.validate(DocumentModelFixtures.longResume())).isEmpty();
        assertValidAgainstSchema(mapper, DocumentModelFixtures.longResume(), "resume-document-model.schema.json");
    }

    @Test
    void coverLetterIsValid() {
        assertThat(validator.validate(DocumentModelFixtures.coverLetter())).isEmpty();
        assertValidAgainstSchema(mapper, DocumentModelFixtures.coverLetter(), "cover-letter-document-model.schema.json");
    }

    @Test
    void longResumeRoundTripsHostileContentExactly() throws Exception {
        ResumeDocumentModel original = DocumentModelFixtures.longResume();

        String json = mapper.writeValueAsString(original);
        ResumeDocumentModel restored = mapper.readValue(json, ResumeDocumentModel.class);

        assertThat(restored).isEqualTo(original);

        String summaryText = restored.summary().text();
        assertThat(summaryText)
                .as("apostrophe + ampersand")
                .contains("O'Brien & Sons")
                .as("em-dash")
                .contains("—")
                .as("non-Latin script: Japanese")
                .contains("東京")
                .as("non-Latin script: Cyrillic")
                .contains("Москва")
                .as("HTML-tag-looking text")
                .contains("<Kubernetes/>")
                .as("curly/straight quotes")
                .contains("\"quoted\"");

        String scriptBullet = restored.sections().get(0).entries().get(0).bullets().get(1).text();
        assertThat(scriptBullet).as("inline script-tag-like hostile content")
                .contains("<script>alert('x')</script>");

        assertThat(restored.sections().get(0).entries().get(0).bullets().get(0).text())
                .as("the one long bullet")
                .isEqualTo(DocumentModelFixtures.LONG_BULLET)
                .hasSizeGreaterThan(500);
    }

    @Test
    void coverLetterRoundTripsThroughJackson() throws Exception {
        CoverLetterDocumentModel original = DocumentModelFixtures.coverLetter();

        String json = mapper.writeValueAsString(original);
        CoverLetterDocumentModel restored = mapper.readValue(json, CoverLetterDocumentModel.class);

        assertThat(restored).isEqualTo(original);
    }
}
