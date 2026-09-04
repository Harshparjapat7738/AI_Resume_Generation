package ai.careerforge.application.document;

import java.util.List;

/**
 * Deterministic, factual coverage information about one assembled document — never a rating, a
 * percentage, or a hiring probability (ADR-036: "no hiring-outcome or ATS score anywhere in this
 * pipeline"). Every field here is something that either did or didn't happen during assembly and
 * validation; there is no scoring surface, only facts a candidate or a future support engineer
 * can check.
 *
 * <p>Shared, deliberately loosely-typed, across {@link ResumeDocumentModel} and
 * {@link CoverLetterDocumentModel}: a cover letter has no {@link SectionHeading}s to omit, so
 * {@code sectionsOmitted} is simply always empty for one and populated (with
 * {@link SectionHeading#name()} values) for the other, rather than this record existing twice.
 *
 * @param evidenceIdsUsed    every evidence id actually cited somewhere in the final,
 *                           already-stripped document
 * @param sectionsOmitted    resume section headings left out entirely because nothing survived
 *                           for them (see {@code DocumentEvidenceValidator}); always empty for a
 *                           cover letter
 * @param contentRemoved     human-readable notes on what was dropped and why — a bullet that
 *                           failed grounding upstream in ai-service, or a leaf stripped here for
 *                           citing an evidence id the profile doesn't actually contain
 * @param truncatedForPageFit human-readable notes on what assembly shortened or left out to
 *                            respect {@link RenderHints#maxPages()}
 */
public record GapReport(
        List<String> evidenceIdsUsed,
        List<String> sectionsOmitted,
        List<String> contentRemoved,
        List<String> truncatedForPageFit) {

    public GapReport {
        evidenceIdsUsed = evidenceIdsUsed == null ? List.of() : List.copyOf(evidenceIdsUsed);
        sectionsOmitted = sectionsOmitted == null ? List.of() : List.copyOf(sectionsOmitted);
        contentRemoved = contentRemoved == null ? List.of() : List.copyOf(contentRemoved);
        truncatedForPageFit = truncatedForPageFit == null ? List.of() : List.copyOf(truncatedForPageFit);
    }

    /** An empty report — nothing omitted, nothing stripped, nothing truncated. */
    public static GapReport empty() {
        return new GapReport(List.of(), List.of(), List.of(), List.of());
    }
}
