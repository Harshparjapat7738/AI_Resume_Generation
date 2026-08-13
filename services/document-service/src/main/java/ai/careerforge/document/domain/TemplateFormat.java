package ai.careerforge.document.domain;

/**
 * The two custom-template source formats (ADR-023). Determines which analyzer/merge engine
 * {@code CustomTemplateAssetService} dispatches to ({@code docx.DocxStructureAnalyzer}/
 * {@code DocxMailMerge} vs {@code pdf.PdfStructureAnalyzer}/{@code PdfMailMerge}) — the two are
 * deliberately never treated as equivalent (different native units, different placeholder-
 * replacement strategy: reflowable runs for DOCX, fixed glyph positions for PDF).
 */
public enum TemplateFormat {
    DOCX,
    PDF
}
