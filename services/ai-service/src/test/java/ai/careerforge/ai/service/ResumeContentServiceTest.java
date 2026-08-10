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
 * Covers the two pure functions that make the degrade path work: flattening generated
 * content into verifiable statements, and removing exactly the statements that failed.
 *
 * <p>Getting removal wrong is dangerous in a quiet way — an off-by-one deletes a good
 * bullet and keeps a fabricated one.
 */
class ResumeContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeContentService service;
    private JsonNode content;

    @BeforeEach
    void setUp() throws Exception {
        service = new ResumeContentService(null, null, null, null, objectMapper);
        content = objectMapper.readTree("""
                {
                  "summary": { "text": "Backend engineer.", "evidenceIds": ["EXP-004"] },
                  "experienceBullets": [
                    { "evidenceId": "EXP-004", "bullets": [
                        { "text": "Built services.", "evidenceIds": ["EXP-004"] },
                        { "text": "Improved latency by 40%.", "evidenceIds": ["EXP-004"] }
                    ]},
                    { "evidenceId": "EXP-005", "bullets": [
                        { "text": "Ran deployments.", "evidenceIds": ["EXP-005"] }
                    ]}
                  ],
                  "projectDescriptions": [
                    { "evidenceId": "PROJ-002", "text": "Route planner.",
                      "evidenceIds": ["PROJ-002"] }
                  ],
                  "skillsOrdering": ["SKILL-001"]
                }""");
    }

    @Nested
    class Extraction {

        @Test
        void flattensEveryStatementWithAStableLocation() {
            List<GeneratedStatement> statements = service.extractStatements(content);

            assertThat(statements).extracting(GeneratedStatement::location)
                    .containsExactly(
                            "summary",
                            "experienceBullets[0].bullets[0]",
                            "experienceBullets[0].bullets[1]",
                            "experienceBullets[1].bullets[0]",
                            "projectDescriptions[0]");
        }

        @Test
        void carriesCitationsThroughForVerification() {
            assertThat(service.extractStatements(content))
                    .first()
                    .satisfies(s -> {
                        assertThat(s.text()).isEqualTo("Backend engineer.");
                        assertThat(s.evidenceIds()).containsExactly("EXP-004");
                    });
        }
    }

    @Nested
    class Removal {

        @Test
        @DisplayName("removes the failing bullet and leaves its sibling untouched")
        void removesOnlyTheOffendingBullet() {
            JsonNode result = service.removeStatements(
                    content, List.of("experienceBullets[0].bullets[1]"));

            JsonNode bullets = result.path("experienceBullets").get(0).path("bullets");
            assertThat(bullets).hasSize(1);
            assertThat(bullets.get(0).path("text").asText()).isEqualTo("Built services.");
        }

        @Test
        void removesTheSummary() {
            JsonNode result = service.removeStatements(content, List.of("summary"));

            assertThat(result.has("summary")).isFalse();
            assertThat(result.path("experienceBullets")).hasSize(2);
        }

        @Test
        @DisplayName("drops an experience once all of its bullets are gone")
        void dropsEmptiedExperienceGroups() {
            JsonNode result = service.removeStatements(
                    content, List.of("experienceBullets[1].bullets[0]"));

            assertThat(result.path("experienceBullets")).hasSize(1);
            assertThat(result.path("experienceBullets").get(0).path("evidenceId").asText())
                    .isEqualTo("EXP-004");
        }

        @Test
        @DisplayName("handles several removals without index drift")
        void removesMultipleLocationsCorrectly() {
            JsonNode result = service.removeStatements(content, List.of(
                    "experienceBullets[0].bullets[0]",
                    "experienceBullets[0].bullets[1]",
                    "projectDescriptions[0]"));

            assertThat(result.path("experienceBullets")).hasSize(1);
            assertThat(result.path("experienceBullets").get(0).path("evidenceId").asText())
                    .isEqualTo("EXP-005");
            assertThat(result.path("projectDescriptions")).isEmpty();
        }

        @Test
        void leavesContentUnchangedWhenNothingFailed() {
            assertThat(service.removeStatements(content, List.of())).isEqualTo(content);
        }

        @Test
        void doesNotMutateTheOriginal() {
            service.removeStatements(content, List.of("summary"));

            assertThat(content.has("summary")).isTrue();
        }
    }
}
