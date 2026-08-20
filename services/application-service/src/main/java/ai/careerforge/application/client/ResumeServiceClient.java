package ai.careerforge.application.client;

import ai.careerforge.application.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.application.client.ClientDtos.TemplateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Recreated after an accidental deletion left {@link ai.careerforge.application.service.ApplicationService}
 * (its {@code attachResume}/{@code create} template check), {@code ApplicationController}'s
 * attach-resume endpoint and both existing test suites referencing a client that no longer
 * existed anywhere in source, breaking the module's compile — see git history around
 * "remove gemini resume generation". Every method signature here is exactly what that
 * surrounding, still-intact code already assumed; nothing about the attach-resume feature
 * itself changed.
 */
@FeignClient(name = "resume-service", configuration = FeignHeaderForwardingConfig.class)
public interface ResumeServiceClient {

    /** {@code GET /api/resumes/{id}} — used by {@code attachResume} to verify the resume
     *  exists and to cross-check it was generated for the same job description. */
    @GetMapping("/api/resumes/{id}")
    ResumeVersionDto getResume(@PathVariable("id") String id);

    /** {@code GET /api/resumes/templates/{id}} (ARCHITECTURE_DECISIONS.md ADR-016) — used by
     *  {@code create} to reject an unknown or unowned {@code templateId}. */
    @GetMapping("/api/resumes/templates/{id}")
    TemplateDto getTemplate(@PathVariable("id") String id);
}
