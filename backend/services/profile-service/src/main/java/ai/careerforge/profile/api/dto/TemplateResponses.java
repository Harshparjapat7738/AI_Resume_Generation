package ai.careerforge.profile.api.dto;

import java.time.Instant;

public final class TemplateResponses {

    private TemplateResponses() {
    }

    /** Deliberately excludes {@code objectKey}/{@code bucket} — internal storage coordinates,
     *  never exposed over HTTP (no public file URL; every download goes through this service's
     *  own ownership-checked streaming endpoint). */
    public record TemplateResponse(
            String id,
            String name,
            String fileName,
            String fileType,
            String documentType,
            boolean isDefault,
            long byteSize,
            Instant createdAt,
            Instant updatedAt) {
    }
}
