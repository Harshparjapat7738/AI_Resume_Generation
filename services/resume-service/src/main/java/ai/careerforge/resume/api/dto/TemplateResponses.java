package ai.careerforge.resume.api.dto;

import java.util.List;

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
            boolean atsSafe) {
    }
}
