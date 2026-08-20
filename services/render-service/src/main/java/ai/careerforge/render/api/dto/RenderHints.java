package ai.careerforge.render.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Presentation-only knobs application-service decided as part of assembly — kept structurally
 * separate from every content field (ADR-036: "renderHints separate from content"). Nothing
 * here is a candidate-facing fact.
 *
 * @param pageSize       physical page size
 * @param maxPages       the page budget assembly targeted
 * @param fontFamily     one allowlisted font family
 * @param accentColorHex optional {@code #RRGGBB} accent colour; {@code null} uses the
 *                       template's own default
 */
public record RenderHints(
        @NotNull PageSize pageSize,
        @Min(1) @Max(3) int maxPages,
        @NotNull FontFamily fontFamily,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String accentColorHex) {
}
