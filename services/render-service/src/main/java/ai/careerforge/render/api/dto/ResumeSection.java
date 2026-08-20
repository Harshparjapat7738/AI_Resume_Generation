package ai.careerforge.render.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** One ordered resume section: an ATS-standard heading plus the entries under it, in document
 *  order. A section with nothing to show is simply absent from the request — application-service
 *  already decided that upstream (see {@code GapReport.sectionsOmitted}), so render-service
 *  never receives an empty section to skip. */
public record ResumeSection(
        @NotNull SectionHeading heading,
        @NotEmpty @Valid List<SectionEntry> entries) {

    public ResumeSection {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
