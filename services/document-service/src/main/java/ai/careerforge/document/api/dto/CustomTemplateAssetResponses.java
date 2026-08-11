package ai.careerforge.document.api.dto;

import java.time.Instant;
import java.util.List;

public final class CustomTemplateAssetResponses {

    private CustomTemplateAssetResponses() {
    }

    public record TemplateStructureResponse(
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

    public record DetectedFieldResponse(String token, String context, String suggestedField) {
    }

    public record CustomTemplateAssetResponse(
            String id,
            String originalFilename,
            long byteSize,
            String sha256,
            TemplateStructureResponse structure,
            List<DetectedFieldResponse> detectedFields,
            Instant createdAt) {
    }
}
