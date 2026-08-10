package ai.careerforge.ai.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.grounding.GroundingValidator.GeneratedStatement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The most important test suite in the platform.
 *
 * <p>Everything else being correct still ships a false claim in a real person's resume if
 * these checks fail. Each test names a specific fabrication the blueprint forbids (§13,
 * §18) and asserts it is caught.
 */
class GroundingValidatorTest {

    private final GroundingValidator validator = new GroundingValidator();

    private static final EvidenceItem EXPERIENCE = new EvidenceItem(
            "EXP-004", "EXPERIENCE", "Backend Engineer", "Northwind Logistics",
            "Built and maintained order-processing services. Improved checkout performance.",
            List.of("Java", "Spring Boot", "MongoDB"),
            List.of("reduced average response time from 800ms to 300ms"),
            "2021-03", "2024-01");

    private static final EvidenceItem PROJECT = new EvidenceItem(
            "PROJ-002", "PROJECT", "Route Optimiser", null,
            "Personal project that plans delivery routes.",
            List.of("Python"), List.of(), "2023", "2023");

    private static final List<EvidenceItem> INVENTORY = List.of(EXPERIENCE, PROJECT);

    private GroundingReport check(String text, String... evidenceIds) {
        return validator.validate(
                List.of(new GeneratedStatement("summary", text, List.of(evidenceIds))),
                INVENTORY);
    }

    @Nested
    @DisplayName("accepts content that is fully supported")
    class Accepts {

        @Test
        void plainRephrasingOfEvidence() {
            GroundingReport report = check(
                    "Built and maintained order-processing services using Java and Spring Boot.",
                    "EXP-004");

            assertThat(report.passed()).isTrue();
            assertThat(report.violations()).isEmpty();
        }

        @Test
        void metricThatAppearsInTheEvidence() {
            GroundingReport report = check(
                    "Reduced average response time from 800ms to 300ms.", "EXP-004");

            assertThat(report.passed()).isTrue();
        }

        @Test
        void datesPresentInTheEvidence() {
            GroundingReport report = check("Worked at Northwind Logistics from 2021 to 2024.",
                    "EXP-004");

            assertThat(report.passed()).isTrue();
        }

        @Test
        void differentlyFormattedButIdenticalNumber() {
            EvidenceItem item = new EvidenceItem("EXP-009", "EXPERIENCE", "Analyst", "Acme",
                    "Supported 1500 monthly users.", List.of(), List.of(), "2022", "2023");

            GroundingReport report = validator.validate(
                    List.of(new GeneratedStatement("summary", "Supported 1,500 monthly users.",
                            List.of("EXP-009"))),
                    List.of(item));

            assertThat(report.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("rejects fabricated facts")
    class Rejects {

        @Test
        @DisplayName("an invented percentage — the classic resume lie")
        void inventedMetric() {
            GroundingReport report = check(
                    "Improved checkout performance by 40% across the platform.", "EXP-004");

            assertThat(report.passed()).isFalse();
            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.INVENTED_METRIC);
        }

        @Test
        void technologyTheCandidateNeverListed() {
            GroundingReport report = check(
                    "Built order-processing services with Java and Kubernetes.", "EXP-004");

            assertThat(report.passed()).isFalse();
            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.UNSUPPORTED_ENTITY
                            && v.detail().equals("Kubernetes"));
        }

        @Test
        void employerThatDoesNotExistInTheProfile() {
            GroundingReport report = check("Delivered services at Globex Corporation.",
                    "EXP-004");

            assertThat(report.passed()).isFalse();
            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.UNSUPPORTED_ENTITY);
        }

        @Test
        void dateOutsideTheEvidence() {
            GroundingReport report = check("Led the team from 2018 onwards.", "EXP-004");

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.UNSUPPORTED_DATE
                            && v.detail().equals("2018"));
        }

        @Test
        void evidenceIdThatWasNeverSupplied() {
            GroundingReport report = check("Built services.", "EXP-999");

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.UNKNOWN_EVIDENCE_ID
                            && "EXP-999".equals(v.evidenceId()));
        }

        @Test
        void statementWithNoCitationAtAll() {
            GroundingReport report = validator.validate(
                    List.of(new GeneratedStatement("summary", "Highly motivated engineer.",
                            List.of())),
                    INVENTORY);

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.MISSING_EVIDENCE_ID);
        }

        @Test
        void anyUrl() {
            GroundingReport report = check(
                    "See the project at https://example.com/portfolio.", "PROJ-002");

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.EXTERNAL_URL);
        }

        @Test
        void anEmailAddress() {
            GroundingReport report = check("Contact hire.me@example.com for details.",
                    "EXP-004");

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.UNSUPPORTED_CONTACT);
        }

        @Test
        @DisplayName("zero-width characters, which hide text from humans and break ATS parsing")
        void hiddenCharacters() {
            GroundingReport report = check("Built order​processing services.", "EXP-004");

            assertThat(report.violations())
                    .anyMatch(v -> v.rule() == GroundingViolation.Rule.HIDDEN_CHARACTERS);
        }
    }

    @Nested
    @DisplayName("report supports the degrade path")
    class DegradePath {

        @Test
        void namesEveryFailingLocationSoItCanBeRemoved() {
            GroundingReport report = validator.validate(List.of(
                    new GeneratedStatement("summary", "Improved throughput by 60%.",
                            List.of("EXP-004")),
                    new GeneratedStatement("experienceBullets[0].bullets[0]",
                            "Built services with Java.", List.of("EXP-004")),
                    new GeneratedStatement("projectDescriptions[0]",
                            "Used Terraform to deploy.", List.of("PROJ-002"))),
                    INVENTORY);

            assertThat(report.passed()).isFalse();
            assertThat(report.failedLocations())
                    .containsExactlyInAnyOrder("summary", "projectDescriptions[0]");
        }

        @Test
        void summaryNeverLeaksGeneratedContent() {
            GroundingReport report = check("Improved performance by 40%.", "EXP-004");

            assertThat(report.summary())
                    .contains("grounding failed")
                    .doesNotContain("Improved performance");
        }
    }
}
