package ai.careerforge.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link DocumentEvidenceValidator} against the exact semantics
 * {@code JdOptimizationService.stripUnknownIds} already applies to JD optimizations: an unknown
 * evidenceId is never trusted, a leaf left with none is removed, and a section left with no
 * entries is removed and reported — never silently.
 */
class DocumentEvidenceValidatorTest {

    private final DocumentEvidenceValidator validator = new DocumentEvidenceValidator();

    @Nested
    @DisplayName("resume: unknown evidenceId")
    class ResumeUnknownIds {

        @Test
        @DisplayName("an entry citing an unknown evidenceId is removed entirely, bullets included")
        void entryWithUnknownEvidenceIdIsRemoved() {
            ResumeDocumentModel model = DocumentModelFixtures.shortResume(); // one entry, EXP-001

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of());

            assertThat(result.sections()).isEmpty(); // the only entry, and therefore the only section, is gone
            assertThat(result.gapReport().sectionsOmitted()).containsExactly("EXPERIENCE");
            assertThat(result.gapReport().contentRemoved())
                    .anyMatch(note -> note.contains("EXP-001") && note.contains("evidenceId not found"));
            assertThat(result.gapReport().evidenceIdsUsed()).isEmpty();
        }

        @Test
        @DisplayName("a bullet citing only an unknown evidenceId is removed; its entry and section survive")
        void bulletWithUnknownEvidenceIdIsRemovedButEntrySurvives() {
            SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", null,
                    null, null, List.of(
                            new ContentLeaf("Shipped the payments service.", List.of("EXP-001"),
                                    ContentOrigin.VERBATIM_FROM_PROFILE),
                            new ContentLeaf("Invented achievement.", List.of("PROJ-999"),
                                    ContentOrigin.REPHRASED_FROM_PROFILE)));
            ResumeDocumentModel model = new ResumeDocumentModel(DocumentModelFixtures.SCHEMA_VERSION,
                    DocumentModelFixtures.header(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))),
                    DocumentModelFixtures.renderHints(), GapReport.empty());

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            assertThat(result.sections()).hasSize(1);
            SectionEntry survivingEntry = result.sections().get(0).entries().get(0);
            assertThat(survivingEntry.bullets()).hasSize(1);
            assertThat(survivingEntry.bullets().get(0).text()).isEqualTo("Shipped the payments service.");
            assertThat(result.gapReport().sectionsOmitted()).isEmpty();
            assertThat(result.gapReport().contentRemoved())
                    .anyMatch(note -> note.contains("PROJ-999"));
        }

        @Test
        @DisplayName("a leaf citing some known and some unknown ids keeps only the known ones")
        void leafWithMixedKnownAndUnknownIdsIsPrunedNotRemoved() {
            SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", null,
                    null, null, List.of(
                            new ContentLeaf("Cross-functional achievement.", List.of("EXP-001", "PROJ-999"),
                                    ContentOrigin.REPHRASED_FROM_PROFILE)));
            ResumeDocumentModel model = new ResumeDocumentModel(DocumentModelFixtures.SCHEMA_VERSION,
                    DocumentModelFixtures.header(), null,
                    List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))),
                    DocumentModelFixtures.renderHints(), GapReport.empty());

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            ContentLeaf survivingLeaf = result.sections().get(0).entries().get(0).bullets().get(0);
            assertThat(survivingLeaf.evidenceIds()).containsExactly("EXP-001");
            assertThat(result.gapReport().contentRemoved())
                    .anyMatch(note -> note.contains("PROJ-999") && note.contains("pruned"));
        }

        @Test
        @DisplayName("a section with more than one entry only loses the entry citing unknown evidence")
        void onlyTheOffendingEntryIsRemovedFromAMultiEntrySection() {
            ResumeDocumentModel model = DocumentModelFixtures.longResume();

            // EXPERIENCE has two entries: EXP-001 and EXP-002. Only EXP-001 is "known".
            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            ResumeSection experience = result.sections().stream()
                    .filter(s -> s.heading() == SectionHeading.EXPERIENCE).findFirst().orElseThrow();
            assertThat(experience.entries()).hasSize(1);
            assertThat(experience.entries().get(0).evidenceId()).isEqualTo("EXP-001");
            // Every other section (EDUCATION, SKILLS, PROJECTS, CERTIFICATIONS, ACHIEVEMENTS)
            // cites an id this test never declared known, so all of them are omitted.
            assertThat(result.gapReport().sectionsOmitted())
                    .containsExactlyInAnyOrder("EDUCATION", "SKILLS", "PROJECTS", "CERTIFICATIONS", "ACHIEVEMENTS");
            // The summary cites EXP-001 and EXP-002; only EXP-001 survives.
            assertThat(result.summary().evidenceIds()).containsExactly("EXP-001");
        }

        @Test
        @DisplayName("evidenceIdsUsed reflects only what actually survives, not what was originally cited")
        void evidenceIdsUsedIsRecomputedFromSurvivingContent() {
            ResumeDocumentModel model = DocumentModelFixtures.shortResume();

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            assertThat(result.gapReport().evidenceIdsUsed()).containsExactly("EXP-001");
        }

        @Test
        @DisplayName("an already-clean model with every id known is returned with no removals")
        void everythingKnownProducesNoRemovals() {
            ResumeDocumentModel model = DocumentModelFixtures.shortResume();

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            assertThat(result.sections()).isEqualTo(model.sections());
            assertThat(result.gapReport().sectionsOmitted()).isEmpty();
            assertThat(result.gapReport().contentRemoved()).isEmpty();
        }

        @Test
        @DisplayName("a pre-existing gap report's truncatedForPageFit notes survive validation untouched")
        void preExistingGapReportNotesAreMergedNotDiscarded() {
            GapReport priorGaps = new GapReport(List.of(), List.of(),
                    List.of(), List.of("shortened the EXP-001 bullet to fit page 1"));
            ResumeDocumentModel base = DocumentModelFixtures.shortResume();
            ResumeDocumentModel model = new ResumeDocumentModel(base.schemaVersion(), base.header(),
                    base.summary(), base.sections(), base.renderHints(), priorGaps);

            ResumeDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            assertThat(result.gapReport().truncatedForPageFit())
                    .containsExactly("shortened the EXP-001 bullet to fit page 1");
        }
    }

    @Nested
    @DisplayName("cover letter: unknown evidenceId")
    class CoverLetterUnknownIds {

        @Test
        @DisplayName("a paragraph citing only an unknown evidenceId is removed")
        void paragraphWithUnknownEvidenceIdIsRemoved() {
            CoverLetterDocumentModel model = DocumentModelFixtures.coverLetter(); // both paragraphs cite EXP-001

            CoverLetterDocumentModel result = validator.stripUnknownIds(model, Set.of());

            assertThat(result.paragraphs()).isEmpty();
            assertThat(result.gapReport().contentRemoved()).isNotEmpty();
            assertThat(result.gapReport().evidenceIdsUsed()).isEmpty();
        }

        @Test
        @DisplayName("a paragraph citing a known evidenceId survives untouched")
        void paragraphWithKnownEvidenceIdSurvives() {
            CoverLetterDocumentModel model = DocumentModelFixtures.coverLetter();

            CoverLetterDocumentModel result = validator.stripUnknownIds(model, Set.of("EXP-001"));

            assertThat(result.paragraphs()).isEqualTo(model.paragraphs());
            assertThat(result.gapReport().contentRemoved()).isEmpty();
            assertThat(result.gapReport().evidenceIdsUsed()).containsExactly("EXP-001");
        }
    }
}
