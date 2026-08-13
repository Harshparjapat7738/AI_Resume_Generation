package ai.careerforge.document.client;

import ai.careerforge.document.client.ClientDtos.TemplateFieldMappingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Fetches a custom template's saved field mapping (ADR-023) — the one piece of the resume-
 * service-owned {@code Template} catalogue row {@code DocumentRenderService} needs when the
 * *main* render endpoint (as opposed to the dedicated
 * {@code POST /api/documents/custom-templates/{id}/generate} endpoint, which the frontend
 * already supplies a mapping to directly) is asked to render a custom template. The ownership
 * check on {@code GET /api/resumes/templates/{id}} already scopes this to the caller's own
 * template — a second, redundant check here would only duplicate it.
 */
@FeignClient(name = "resume-service", contextId = "templateServiceClient", configuration = FeignHeaderForwardingConfig.class)
public interface TemplateServiceClient {

    @GetMapping("/api/resumes/templates/{id}")
    TemplateFieldMappingDto getTemplate(@PathVariable("id") String id);
}
