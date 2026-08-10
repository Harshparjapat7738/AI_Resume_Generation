package ai.careerforge.document.api.dto;

public final class DocumentRequests {

    private DocumentRequests() {
    }

    /** {@code templateId} is optional — omit it to use the default template (modern-ats) or,
     *  on a re-render, to keep the template already used for this resume version. */
    public record RenderRequest(String templateId) {
    }
}
