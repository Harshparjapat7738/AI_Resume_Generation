package ai.careerforge.jd.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class JdRequests {

    private JdRequests() {
    }

    /** Mirrors the 50-60,000 character bound ai-service enforces on the text it forwards to Groq. */
    public record SubmitJdRequest(@NotBlank @Size(min = 50, max = 60_000) String jobDescriptionText) {
    }

    /** Scheme/host/private-network validation happens in {@code SsrfGuard}, not here —
     *  this only bounds the raw input size before it reaches that. */
    public record FetchUrlRequest(@NotBlank @Size(max = 2000) String url) {
    }
}
