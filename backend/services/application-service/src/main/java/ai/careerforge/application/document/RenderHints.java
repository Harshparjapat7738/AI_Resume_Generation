package ai.careerforge.application.document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Presentation-only knobs for {@code render-service} — deliberately its own top-level field,
 * never mixed into content records (ADR-036: "renderHints separate from content"). Nothing in
 * this record is a candidate-facing fact, nothing here carries an {@code evidenceId}, and
 * changing a value here never changes what the document says — only how it looks.
 *
 * @param pageSize       physical page size
 * @param maxPages       the hard page budget assembly targeted when deciding what to include
 *                       vs. truncate (see {@link GapReport#truncatedForPageFit()})
 * @param fontFamily     one allowlisted font family; render-service embeds nothing else
 * @param accentColorHex optional {@code #RRGGBB} accent colour; {@code null} uses the
 *                       template's own default
 */
public record RenderHints(
        @NotNull PageSize pageSize,
        @Min(1) @Max(3) int maxPages,
        @NotNull FontFamily fontFamily,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String accentColorHex) {
}
