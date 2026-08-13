package ai.careerforge.document.domain;

/**
 * One {@code {{token}}} placeholder found in the uploaded document, plus a short excerpt of
 * the surrounding paragraph text so a user reviewing the mapping editor can tell which one is
 * which without opening the file. {@code suggestedField} is this analyzer's best deterministic
 * guess (see {@code docx.ProfileFieldCatalog}) — {@code null} if nothing matched — and is only
 * ever a suggestion: the caller decides the real mapping.
 *
 * <p>The PDF-specific fields (ADR-023) are {@code null} for a DOCX-sourced field — DOCX
 * placeholders live inside reflowable paragraph runs with no fixed position to record. For a
 * PDF-sourced field they are the exact geometry {@code pdf.PdfStructureAnalyzer} read off the
 * page: which page, the placeholder's baseline origin and bounding box (PDF points — 1/72
 * inch), and the live font/size/color it was set in, everything {@code pdf.PdfMailMerge} needs
 * to redact and redraw only that region without touching the rest of the page.
 */
public record DetectedField(
        String token,
        String context,
        String suggestedField,
        Integer pdfPage,
        Float pdfX,
        Float pdfY,
        Float pdfWidth,
        Float pdfHeight,
        Float pdfFontSizePt,
        String pdfFontName,
        String pdfColorHex) {

    /** DOCX-sourced field — no fixed position. */
    public DetectedField(String token, String context, String suggestedField) {
        this(token, context, suggestedField, null, null, null, null, null, null, null, null);
    }

    public boolean hasPdfPosition() {
        return pdfPage != null && pdfX != null && pdfY != null;
    }
}
