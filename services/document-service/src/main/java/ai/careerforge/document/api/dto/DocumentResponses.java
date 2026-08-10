package ai.careerforge.document.api.dto;

import java.time.Instant;

public final class DocumentResponses {

    private DocumentResponses() {
    }

    public record RenderedDocumentResponse(
            String id,
            String resumeVersionId,
            String format,
            String templateId,
            String templateVersion,
            int pageCount,
            long byteSize,
            String sha256,
            Instant renderedAt) {
    }
}
