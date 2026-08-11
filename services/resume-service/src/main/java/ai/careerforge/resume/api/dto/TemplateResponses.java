package ai.careerforge.resume.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TemplateResponses {

    private TemplateResponses() {
    }

    public record TemplateResponse(
            String templateId,
            String name,
            String description,
            String previewKey,
            String type,
            String version,
            String status,
            String source,
            List<String> supportedFormats,
            boolean atsSafe,
            // ---- custom-upload-only fields, null/empty for BUILT_IN and ONLINE ----
            String ownerUserId,
            String originalFilename,
            Map<String, Object> structure,
            List<Map<String, Object>> detectedFields,
            Map<String, String> fieldMappings,
            boolean isDefault,
            Instant createdAt) {
    }
}
