package ai.careerforge.application.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One ordered section of a resume: an ATS-standard {@link SectionHeading} plus the entries
 * presented under it. Order within {@link ResumeDocumentModel#sections()} and order within
 * {@code entries} are both document order — a deterministic decision
 * {@code application-service}'s assembly makes, never asked of the LLM (ADR-036).
 *
 * <p>{@code entries} is {@code @NotEmpty}: a section with nothing to show is never included in
 * the model in the first place — it is omitted and recorded in
 * {@link GapReport#sectionsOmitted()} instead of appearing here empty.
 */
public record ResumeSection(
        @NotNull SectionHeading heading,
        @NotEmpty @Valid List<SectionEntry> entries) {

    public ResumeSection {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
