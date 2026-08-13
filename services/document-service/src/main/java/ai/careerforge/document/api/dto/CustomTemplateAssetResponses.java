package ai.careerforge.document.api.dto;

import java.time.Instant;
import java.util.List;

public final class CustomTemplateAssetResponses {

    private CustomTemplateAssetResponses() {
    }

    /** {@code sourceType} (ADR-023) discriminates which field group is populated — see
     *  {@code domain.TemplateStructure}'s own comment. A {@code DOCX} row leaves the PDF-only
     *  fields ({@code pageCount}/{@code pageWidthPt}/{@code pageHeightPt}) null; a {@code PDF}
     *  row leaves the DOCX-only ones (twips, {@code columnCount}, {@code paragraphCount}) null. */
    public record TemplateStructureResponse(
            String sourceType,
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
            Integer pageCount,
            Float pageWidthPt,
            Float pageHeightPt,
            List<String> fontsUsed,
            List<Double> fontSizesPt,
            List<String> colorsUsedHex,
            List<String> alignmentsUsed,
            List<String> headingsFound) {
    }

    /** The PDF-specific geometry fields are {@code null} for a field detected in a DOCX. */
    public record DetectedFieldResponse(
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
    }

    public record CustomTemplateAssetResponse(
            String id,
            String originalFilename,
            String format,
            long byteSize,
            String sha256,
            TemplateStructureResponse structure,
            List<DetectedFieldResponse> detectedFields,
            Instant createdAt) {
    }
}
