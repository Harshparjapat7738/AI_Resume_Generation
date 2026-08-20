package ai.careerforge.render.api;

import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.RenderResponse;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.service.DocumentRenderer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal rendering endpoints (ADR-036).
 *
 * <p><strong>Not routed through the API Gateway</strong> — like {@code ai-service}'s own
 * internal endpoints, this is called only from {@code application-service}, over Eureka/Feign,
 * never from a browser.
 *
 * <p>Deliberately thin: {@code @Valid} rejects a malformed request before this class's methods
 * even run (platform-common's {@code GlobalExceptionHandler} turns that into a standard
 * {@code ApiError}), and every other outcome — success or a failure anywhere in the render
 * pipeline — is already a complete {@link RenderResponse} by the time
 * {@link DocumentRenderer} returns it. This class does no rendering, no error mapping and no
 * decision-making of its own; it only validates the shape and returns 200.
 */
@RestController
@RequestMapping("/internal/render")
public class RenderController {

    private final DocumentRenderer documentRenderer;

    public RenderController(DocumentRenderer documentRenderer) {
        this.documentRenderer = documentRenderer;
    }

    @PostMapping("/resume")
    public ResponseEntity<RenderResponse> renderResume(@Valid @RequestBody ResumeRenderRequest request) {
        return ResponseEntity.ok(documentRenderer.renderResume(request));
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<RenderResponse> renderCoverLetter(@Valid @RequestBody CoverLetterRenderRequest request) {
        return ResponseEntity.ok(documentRenderer.renderCoverLetter(request));
    }
}
