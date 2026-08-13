package ai.careerforge.document.domain;

import java.util.List;

/**
 * Real structural/style facts extracted directly from an uploaded custom template — see
 * {@code docx.DocxStructureAnalyzer} for DOCX, {@code pdf.PdfStructureAnalyzer} for PDF —
 * nothing here is inferred or guessed.
 *
 * <p>{@code sourceType} discriminates which field group is populated (ADR-023): a DOCX row
 * fills the OOXML-native fields (measurements in twips — twentieths of a point, 1440 = 1 inch —
 * left unconverted so no rounding/unit-conversion bug can silently drift from what the file
 * actually contains) and leaves the PDF-only fields {@code null}; a PDF row fills
 * {@code pageCount}/{@code pageWidthPt}/{@code pageHeightPt} (PDF's native unit, points — 1/72
 * inch) and leaves the DOCX-only fields ({@code columnCount}, twips, {@code paragraphCount})
 * null/zero. The two are never treated as equivalent — a PDF genuinely has no OOXML section
 * properties to read, and a DOCX has no fixed page-content-stream geometry — so this is one
 * shape with two disjoint, clearly-named field groups (the same convention {@code Template}
 * already uses for its BUILT_IN-vs-CUSTOM_UPLOAD-only fields), not a claim that both formats
 * produce the same facts.
 */
public record TemplateStructure(
        TemplateFormat sourceType,
        // ---- DOCX-only (null/0 for PDF) --------------------------------------------------
        Integer pageWidthTwips,
        Integer pageHeightTwips,
        Integer marginTopTwips,
        Integer marginBottomTwips,
        Integer marginLeftTwips,
        Integer marginRightTwips,
        int columnCount,
        int paragraphCount,
        int tableCount,
        boolean hasHeader,
        boolean hasFooter,
        // ---- PDF-only (null for DOCX) ------------------------------------------------------
        Integer pageCount,
        Float pageWidthPt,
        Float pageHeightPt,
        // ---- shared -------------------------------------------------------------------------
        List<String> fontsUsed,
        List<Double> fontSizesPt,
        List<String> colorsUsedHex,
        List<String> alignmentsUsed,
        List<String> headingsFound) {

    /** DOCX analyzer convenience — keeps {@code DocxStructureAnalyzer} unaware of the PDF-only
     *  fields it will never populate. */
    public static TemplateStructure forDocx(
            Integer pageWidthTwips, Integer pageHeightTwips, Integer marginTopTwips, Integer marginBottomTwips,
            Integer marginLeftTwips, Integer marginRightTwips, int columnCount, List<String> fontsUsed,
            List<Double> fontSizesPt, List<String> colorsUsedHex, List<String> alignmentsUsed,
            List<String> headingsFound, int paragraphCount, int tableCount, boolean hasHeader, boolean hasFooter) {
        return new TemplateStructure(
                TemplateFormat.DOCX, pageWidthTwips, pageHeightTwips, marginTopTwips, marginBottomTwips,
                marginLeftTwips, marginRightTwips, columnCount, paragraphCount, tableCount, hasHeader, hasFooter,
                null, null, null, fontsUsed, fontSizesPt, colorsUsedHex, alignmentsUsed, headingsFound);
    }

    /** PDF analyzer convenience — keeps {@code PdfStructureAnalyzer} unaware of the DOCX-only
     *  fields it will never populate. */
    public static TemplateStructure forPdf(
            Integer pageCount, Float pageWidthPt, Float pageHeightPt, List<String> fontsUsed,
            List<Double> fontSizesPt, List<String> colorsUsedHex, List<String> alignmentsUsed) {
        return new TemplateStructure(
                TemplateFormat.PDF, null, null, null, null, null, null, 0, 0, 0, false, false,
                pageCount, pageWidthPt, pageHeightPt, fontsUsed, fontSizesPt, colorsUsedHex, alignmentsUsed, List.of());
    }
}
