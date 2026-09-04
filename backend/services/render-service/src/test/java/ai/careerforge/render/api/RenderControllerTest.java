package ai.careerforge.render.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.careerforge.render.api.dto.ContentLeaf;
import ai.careerforge.render.api.dto.ContentOrigin;
import ai.careerforge.render.api.dto.CoverLetterRenderRequest;
import ai.careerforge.render.api.dto.DocumentHeader;
import ai.careerforge.render.api.dto.DocumentMetadata;
import ai.careerforge.render.api.dto.FontFamily;
import ai.careerforge.render.api.dto.OutputFormat;
import ai.careerforge.render.api.dto.PageSize;
import ai.careerforge.render.api.dto.RenderError;
import ai.careerforge.render.api.dto.RenderHints;
import ai.careerforge.render.api.dto.RenderResponse;
import ai.careerforge.render.api.dto.RenderTemplate;
import ai.careerforge.render.api.dto.ResumeRenderRequest;
import ai.careerforge.render.api.dto.ResumeSection;
import ai.careerforge.render.api.dto.SectionEntry;
import ai.careerforge.render.api.dto.SectionHeading;
import ai.careerforge.render.service.DocumentRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level coverage for the thin {@link RenderController}: {@code @Valid} rejects a malformed
 * request before {@link DocumentRenderer} is ever called (proving "validate request before
 * rendering" happens at the boundary Spring MVC already owns, not in hand-written code), and a
 * valid request's response — success or failure — passes through exactly as
 * {@link DocumentRenderer} produced it. {@link DocumentRenderer} itself is mocked here
 * deliberately: {@link ai.careerforge.render.service.DocumentRenderServiceTest} already covers
 * the real pipeline; this class covers only the controller's own, narrower job.
 *
 * <p>{@code @WebMvcTest}'s slice only auto-imports Spring Boot's own curated auto-configuration
 * set, not platform-common's {@code PlatformWebAutoConfiguration} (a third-party auto-config from
 * this service's point of view) — so {@code GlobalExceptionHandler} is imported explicitly here,
 * the same class it would otherwise pull in at real application startup.
 */
@WebMvcTest(RenderController.class)
@Import(ai.careerforge.common.error.GlobalExceptionHandler.class)
class RenderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentRenderer documentRenderer;

    private static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", null, null, List.of());
    }

    private static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    private static ResumeRenderRequest validResumeRequest() {
        SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", null,
                null, null, List.of(new ContentLeaf("Shipped a service.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE)));
        return new ResumeRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(), null,
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))), renderHints());
    }

    private static CoverLetterRenderRequest validCoverLetterRequest() {
        return new CoverLetterRenderRequest("1.0.0", RenderTemplate.STANDARD, OutputFormat.PDF, header(),
                "Senior Backend Engineer", "Acme Corp", "Dear Hiring Manager,",
                List.of(new ContentLeaf("I'm writing to apply.", List.of("EXP-001"),
                        ContentOrigin.REPHRASED_FROM_PROFILE)),
                "Sincerely,", "Priya Sharma", renderHints());
    }

    private static RenderResponse succeededResponse() {
        DocumentMetadata metadata = new DocumentMetadata("doc-1", OutputFormat.PDF, 4, 1,
                Instant.parse("2026-08-20T10:00:00Z"));
        return RenderResponse.succeeded(metadata, "%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Nested
    @DisplayName("POST /internal/render/resume")
    class RenderResume {

        @Test
        @DisplayName("a valid request returns 200 with the service's response, unaltered")
        void validRequestReturnsServiceResponse() throws Exception {
            when(documentRenderer.renderResume(any())).thenReturn(succeededResponse());

            mockMvc.perform(post("/internal/render/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validResumeRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.document.documentId").value("doc-1"));

            verify(documentRenderer).renderResume(any());
        }

        @Test
        @DisplayName("a request missing required fields is rejected with 400 before the service is called")
        void invalidRequestIsRejectedBeforeServiceIsCalled() throws Exception {
            String malformed = "{\"template\":\"STANDARD\",\"outputFormat\":\"PDF\"}"; // no schemaVersion, header, sections, renderHints

            mockMvc.perform(post("/internal/render/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(documentRenderer);
        }

        @Test
        @DisplayName("a FAILED service response still returns 200 — rendering failure is a normal outcome, not an HTTP error")
        void failedServiceResponseStillReturns200() throws Exception {
            RenderResponse failed = RenderResponse.failed(
                    List.of(new RenderError("PDF_CONVERSION_FAILED", "conversion failed")));
            when(documentRenderer.renderResume(any())).thenReturn(failed);

            mockMvc.perform(post("/internal/render/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validResumeRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errors[0].code").value("PDF_CONVERSION_FAILED"));
        }
    }

    @Nested
    @DisplayName("POST /internal/render/cover-letter")
    class RenderCoverLetter {

        @Test
        @DisplayName("a valid request returns 200 with the service's response, unaltered")
        void validRequestReturnsServiceResponse() throws Exception {
            when(documentRenderer.renderCoverLetter(any())).thenReturn(succeededResponse());

            mockMvc.perform(post("/internal/render/cover-letter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCoverLetterRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));

            verify(documentRenderer).renderCoverLetter(any());
        }

        @Test
        @DisplayName("a request with no paragraphs is rejected with 400 before the service is called")
        void invalidRequestIsRejectedBeforeServiceIsCalled() throws Exception {
            CoverLetterRenderRequest base = validCoverLetterRequest();
            CoverLetterRenderRequest noParagraphs = new CoverLetterRenderRequest(base.schemaVersion(),
                    base.template(), base.outputFormat(), base.header(), base.targetRole(), base.targetCompany(),
                    base.salutation(), List.of(), base.closing(), base.signatureName(), base.renderHints());

            mockMvc.perform(post("/internal/render/cover-letter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(noParagraphs)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(documentRenderer);
        }
    }
}
