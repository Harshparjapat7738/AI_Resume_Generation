package ai.careerforge.document.domain;

import java.util.List;

/**
 * Real structural/style facts extracted directly from an uploaded DOCX's OOXML (see
 * {@code docx.DocxStructureAnalyzer}) — nothing here is inferred or guessed; every field is
 * either read straight off {@code w:sectPr}/{@code w:pPr}/{@code w:rPr} or counted from the
 * document body. Measurements are DOCX's native unit, twentieths of a point ("twips") —
 * 1440 twips = 1 inch — left unconverted so no rounding/unit-conversion bug can silently
 * drift from what the file actually contains.
 */
public record TemplateStructure(
        Integer pageWidthTwips,
        Integer pageHeightTwips,
        Integer marginTopTwips,
        Integer marginBottomTwips,
        Integer marginLeftTwips,
        Integer marginRightTwips,
        int columnCount,
        List<String> fontsUsed,
        List<Double> fontSizesPt,
        List<String> colorsUsedHex,
        List<String> alignmentsUsed,
        List<String> headingsFound,
        int paragraphCount,
        int tableCount,
        boolean hasHeader,
        boolean hasFooter) {
}
