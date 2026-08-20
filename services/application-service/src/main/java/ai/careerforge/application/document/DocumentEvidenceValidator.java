package ai.careerforge.application.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The document-model analogue of {@code JdOptimizationService.stripUnknownIds} — the same
 * defence, applied to an already-assembled {@link ResumeDocumentModel}/
 * {@link CoverLetterDocumentModel} rather than a JD optimisation's {@code JsonNode}. A citation
 * that survives here is one the caller's known-evidence set really contains; anything else is
 * removed rather than trusted, and the removal is reported, never silently dropped
 * (CLAUDE.md: "Never silently drop content; report every removal").
 *
 * <p>This is a second, independent gate, not a replacement for {@code GroundingValidator}:
 * {@code GroundingValidator} already checked every leaf's prose against the evidence it cites,
 * upstream in {@code ai-service}, before {@code application-service} ever wove it into a
 * document. This class instead checks structural integrity of the assembled document against
 * whatever evidence inventory the caller supplies at validation time — the same defence-in-depth
 * relationship {@code stripUnknownIds} already has with {@code EvidenceSelectionService}'s own
 * upstream checks.
 *
 * <p>Algorithm, leaf to root:
 * <ol>
 *   <li>a {@link ContentLeaf}'s {@code evidenceIds} are pruned to the known set; a leaf left
 *       with none is removed entirely (a leaf cannot exist without evidence — ADR-036)</li>
 *   <li>a {@link SectionEntry} whose own {@code evidenceId} is unknown is removed entirely,
 *       bullets included — its structural facts (title, dates, organisation) have no surviving
 *       anchor, so nothing under it can either</li>
 *   <li>a {@link ResumeSection} left with no entries is removed entirely and its heading
 *       recorded in {@link GapReport#sectionsOmitted()}</li>
 *   <li>the returned model's {@link GapReport} is the input model's own gap report with these
 *       new removals appended, and {@link GapReport#evidenceIdsUsed()} recomputed from what
 *       actually survives — never merely asserted</li>
 * </ol>
 */
@Component
public class DocumentEvidenceValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentEvidenceValidator.class);

    /** Strips citations to unknown evidence, removing any leaf, entry or section left with
     *  none, and returns a new model whose {@link GapReport} accounts for every removal. */
    public ResumeDocumentModel stripUnknownIds(ResumeDocumentModel model, Set<String> knownEvidenceIds) {
        List<String> removalNotes = new ArrayList<>();
        List<String> sectionsOmitted = new ArrayList<>();

        ContentLeaf summary = stripLeaf(model.summary(), knownEvidenceIds, "summary", removalNotes);

        List<ResumeSection> keptSections = new ArrayList<>();
        for (ResumeSection section : model.sections()) {
            List<SectionEntry> keptEntries = new ArrayList<>();
            for (SectionEntry entry : section.entries()) {
                if (!knownEvidenceIds.contains(entry.evidenceId())) {
                    removalNotes.add("removed entry '" + entry.title() + "' (" + entry.evidenceId()
                            + ") from " + section.heading() + ": evidenceId not found in profile");
                    continue;
                }
                List<ContentLeaf> keptBullets = new ArrayList<>();
                for (ContentLeaf bullet : entry.bullets()) {
                    ContentLeaf stripped = stripLeaf(bullet, knownEvidenceIds,
                            "bullet under " + entry.evidenceId(), removalNotes);
                    if (stripped != null) {
                        keptBullets.add(stripped);
                    }
                }
                keptEntries.add(new SectionEntry(entry.evidenceId(), entry.title(), entry.organisation(),
                        entry.location(), entry.startDate(), entry.endDate(), keptBullets));
            }
            if (keptEntries.isEmpty()) {
                sectionsOmitted.add(section.heading().name());
                removalNotes.add("removed section " + section.heading() + ": no entries left with known evidence");
            } else {
                keptSections.add(new ResumeSection(section.heading(), keptEntries));
            }
        }

        if (!removalNotes.isEmpty()) {
            log.warn("Stripped {} unsupported item(s) from resume document model", removalNotes.size());
        }

        Set<String> evidenceIdsUsed = new LinkedHashSet<>();
        if (summary != null) {
            evidenceIdsUsed.addAll(summary.evidenceIds());
        }
        for (ResumeSection section : keptSections) {
            for (SectionEntry entry : section.entries()) {
                evidenceIdsUsed.add(entry.evidenceId());
                for (ContentLeaf bullet : entry.bullets()) {
                    evidenceIdsUsed.addAll(bullet.evidenceIds());
                }
            }
        }

        GapReport merged = mergeGapReport(model.gapReport(), evidenceIdsUsed, sectionsOmitted, removalNotes);

        return new ResumeDocumentModel(model.schemaVersion(), model.header(), summary, keptSections,
                model.renderHints(), merged);
    }

    /** Same defence for a cover letter: no sections to omit, only paragraphs to strip. */
    public CoverLetterDocumentModel stripUnknownIds(CoverLetterDocumentModel model, Set<String> knownEvidenceIds) {
        List<String> removalNotes = new ArrayList<>();
        List<ContentLeaf> keptParagraphs = new ArrayList<>();
        for (ContentLeaf paragraph : model.paragraphs()) {
            ContentLeaf stripped = stripLeaf(paragraph, knownEvidenceIds, "paragraph", removalNotes);
            if (stripped != null) {
                keptParagraphs.add(stripped);
            }
        }

        if (!removalNotes.isEmpty()) {
            log.warn("Stripped {} unsupported paragraph(s) from cover letter document model", removalNotes.size());
        }

        Set<String> evidenceIdsUsed = new LinkedHashSet<>();
        for (ContentLeaf paragraph : keptParagraphs) {
            evidenceIdsUsed.addAll(paragraph.evidenceIds());
        }

        GapReport merged = mergeGapReport(model.gapReport(), evidenceIdsUsed, List.of(), removalNotes);

        return new CoverLetterDocumentModel(model.schemaVersion(), model.header(), model.targetRole(),
                model.targetCompany(), model.salutation(), keptParagraphs, model.closing(),
                model.signatureName(), model.renderHints(), merged);
    }

    /** Prunes {@code leaf}'s evidenceIds to the known set; returns {@code null} (removed,
     *  noted) when nothing survives, otherwise a leaf carrying only surviving citations. */
    private ContentLeaf stripLeaf(ContentLeaf leaf, Set<String> knownEvidenceIds, String location,
                                  List<String> removalNotes) {
        if (leaf == null) {
            return null;
        }
        List<String> survivingIds = new ArrayList<>();
        List<String> unknownIds = new ArrayList<>();
        for (String evidenceId : leaf.evidenceIds()) {
            if (knownEvidenceIds.contains(evidenceId)) {
                survivingIds.add(evidenceId);
            } else {
                unknownIds.add(evidenceId);
            }
        }
        if (survivingIds.isEmpty()) {
            removalNotes.add("removed " + location + ": cites unknown evidenceId(s) " + leaf.evidenceIds());
            return null;
        }
        if (!unknownIds.isEmpty()) {
            removalNotes.add("pruned unknown evidenceId(s) " + unknownIds + " from " + location);
            return new ContentLeaf(leaf.text(), survivingIds, leaf.origin());
        }
        return leaf;
    }

    private GapReport mergeGapReport(GapReport existing, Set<String> evidenceIdsUsed,
                                     List<String> newSectionsOmitted, List<String> newRemovalNotes) {
        List<String> sectionsOmitted = new ArrayList<>(existing.sectionsOmitted());
        sectionsOmitted.addAll(newSectionsOmitted);
        List<String> contentRemoved = new ArrayList<>(existing.contentRemoved());
        contentRemoved.addAll(newRemovalNotes);
        return new GapReport(List.copyOf(evidenceIdsUsed), sectionsOmitted, contentRemoved,
                existing.truncatedForPageFit());
    }
}
