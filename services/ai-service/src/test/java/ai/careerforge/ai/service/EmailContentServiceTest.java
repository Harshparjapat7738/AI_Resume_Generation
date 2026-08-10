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
 * Mirrors {@link CoverLetterContentServiceTest}: covers the two pure functions that make the
 * degrade path work for application emails — flattening generated content into verifiable
 * statements, and removing exactly the paragraph(s) that failed.
 */
class EmailContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EmailContentService service;
    private JsonNode content;

    @BeforeEach
    void setUp() throws Exception {
        service = new EmailContentService(null, null, null, null, objectMapper);
        content = objectMapper.readTree("""
                {
                  "greeting": "Dear Hiring Manager,",
                  "bodyParagraph": { "text": "I have led backend systems handling significant traffic.",
                    "evidenceIds": ["EXP-004"] },
                  "closingParagraph": { "text": "My resume is attached for your review.",
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
                    .containsExactly("bodyParagraph", "closingParagraph");
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
                        assertThat(s.text()).isEqualTo("I have led backend systems handling significant traffic.");
                        assertThat(s.evidenceIds()).containsExactly("EXP-004");
                    });
        }
    }

    @Nested
    class Removal {

        @Test
        @DisplayName("removes the failing paragraph and leaves its sibling untouched")
        void removesOnlyTheOffendingParagraph() {
            JsonNode result = service.removeStatements(content, List.of("bodyParagraph"));

            assertThat(result.has("bodyParagraph")).isFalse();
            assertThat(result.has("closingParagraph")).isTrue();
        }

        @Test
        void removesTheClosingParagraph() {
            JsonNode result = service.removeStatements(content, List.of("closingParagraph"));

            assertThat(result.has("closingParagraph")).isFalse();
            assertThat(result.has("bodyParagraph")).isTrue();
        }

        @Test
        @DisplayName("handles both removals without disturbing greeting/signOff")
        void removesBothParagraphs() {
            JsonNode result = service.removeStatements(content, List.of("bodyParagraph", "closingParagraph"));

            assertThat(result.has("bodyParagraph")).isFalse();
            assertThat(result.has("closingParagraph")).isFalse();
            assertThat(result.path("greeting").asText()).isEqualTo("Dear Hiring Manager,");
            assertThat(result.path("signOff").asText()).isEqualTo("Sincerely,");
        }

        @Test
        void leavesContentUnchangedWhenNothingFailed() {
            assertThat(service.removeStatements(content, List.of())).isEqualTo(content);
        }

        @Test
        void doesNotMutateTheOriginal() {
            service.removeStatements(content, List.of("bodyParagraph"));

            assertThat(content.has("bodyParagraph")).isTrue();
        }
    }
}
