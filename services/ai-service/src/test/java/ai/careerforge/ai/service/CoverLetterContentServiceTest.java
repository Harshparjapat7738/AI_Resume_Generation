package ai.careerforge.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.ai.grounding.GroundingValidator.GeneratedStatement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors {@link ResumeContentServiceTest}: covers the two pure functions that make the
 * degrade path work for cover letters — flattening generated content into verifiable
 * statements, and removing exactly the paragraphs that failed.
 */
class CoverLetterContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CoverLetterContentService service;
    private JsonNode content;

    @BeforeEach
    void setUp() throws Exception {
        service = new CoverLetterContentService(null, null, null, null, objectMapper);
        content = objectMapper.readTree("""
                {
                  "greeting": "Dear Hiring Manager,",
                  "openingParagraph": { "text": "I am applying for the Backend Engineer role.",
                    "evidenceIds": ["EXP-004"] },
                  "bodyParagraphs": [
                    { "text": "Built services.", "evidenceIds": ["EXP-004"] },
                    { "text": "Improved latency by 40%.", "evidenceIds": ["EXP-004"] }
                  ],
                  "closingParagraph": { "text": "I look forward to hearing from you.",
                    "evidenceIds": ["EXP-004"] },
                  "signOff": "Sincerely,"
                }""");
    }

    @Nested
    class Extraction {

        @Test
        void flattensEveryParagraphWithAStableLocation() {
            List<GeneratedStatement> statements = service.extractStatements(content);

            assertThat(statements).extracting(GeneratedStatement::location)
                    .containsExactly(
                            "openingParagraph",
                            "bodyParagraphs[0]",
                            "bodyParagraphs[1]",
                            "closingParagraph");
        }

        @Test
        void greetingAndSignOffAreNeverTreatedAsStatements() {
            assertThat(service.extractStatements(content))
                    .noneMatch(s -> s.location().equals("greeting") || s.location().equals("signOff"));
        }

        @Test
        void carriesCitationsThroughForVerification() {
            assertThat(service.extractStatements(content))
                    .first()
                    .satisfies(s -> {
                        assertThat(s.text()).isEqualTo("I am applying for the Backend Engineer role.");
                        assertThat(s.evidenceIds()).containsExactly("EXP-004");
                    });
        }
    }

    @Nested
    class Removal {

        @Test
        @DisplayName("removes the failing paragraph and leaves its sibling untouched")
        void removesOnlyTheOffendingParagraph() {
            JsonNode result = service.removeStatements(content, List.of("bodyParagraphs[1]"));

            JsonNode paragraphs = result.path("bodyParagraphs");
            assertThat(paragraphs).hasSize(1);
            assertThat(paragraphs.get(0).path("text").asText()).isEqualTo("Built services.");
        }

        @Test
        void removesTheOpeningParagraph() {
            JsonNode result = service.removeStatements(content, List.of("openingParagraph"));

            assertThat(result.has("openingParagraph")).isFalse();
            assertThat(result.path("bodyParagraphs")).hasSize(2);
            assertThat(result.has("closingParagraph")).isTrue();
        }

        @Test
        void removesTheClosingParagraph() {
            JsonNode result = service.removeStatements(content, List.of("closingParagraph"));

            assertThat(result.has("closingParagraph")).isFalse();
            assertThat(result.has("openingParagraph")).isTrue();
        }

        @Test
        @DisplayName("handles several removals without index drift")
        void removesMultipleLocationsCorrectly() {
            JsonNode result = service.removeStatements(content, List.of(
                    "bodyParagraphs[0]", "bodyParagraphs[1]", "closingParagraph"));

            assertThat(result.path("bodyParagraphs")).isEmpty();
            assertThat(result.has("closingParagraph")).isFalse();
            assertThat(result.has("openingParagraph")).isTrue();
        }

        @Test
        void leavesContentUnchangedWhenNothingFailed() {
            assertThat(service.removeStatements(content, List.of())).isEqualTo(content);
        }

        @Test
        void doesNotMutateTheOriginal() {
            service.removeStatements(content, List.of("openingParagraph"));

            assertThat(content.has("openingParagraph")).isTrue();
        }
    }
}
