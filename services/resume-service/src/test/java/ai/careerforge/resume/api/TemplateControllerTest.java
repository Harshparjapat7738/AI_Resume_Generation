package ai.careerforge.resume.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.GlobalExceptionHandler;
import ai.careerforge.common.security.CallerIdArgumentResolver;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateSource;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.service.TemplateService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Covers "template listing" and "template preview" from the task's test list. */
@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

    @Mock
    private TemplateService templateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TemplateController(templateService))
                .setCustomArgumentResolvers(new CallerIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsTheBuiltInCatalogueShape() throws Exception {
        Template classic = new Template("classic", "Classic", "A classic layout.", "classic",
                TemplateType.RESUME, "1", TemplateStatus.ACTIVE, TemplateSource.BUILT_IN, null,
                List.of("PDF", "DOCX"), true);
        when(templateService.list(eq("user-1"), eq(TemplateType.RESUME))).thenReturn(List.of(classic));

        mockMvc.perform(get("/api/resumes/templates").param("type", "RESUME").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].templateId").value("classic"))
                .andExpect(jsonPath("$[0].name").value("Classic"))
                .andExpect(jsonPath("$[0].previewKey").value("classic"))
                .andExpect(jsonPath("$[0].source").value("BUILT_IN"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].atsSafe").value(true));
    }

    @Test
    void getReturnsASingleTemplate() throws Exception {
        Template modern = new Template("modern-ats", "Modern ATS", "desc", "modern-ats",
                TemplateType.RESUME, "1", TemplateStatus.ACTIVE, TemplateSource.BUILT_IN, null,
                List.of("PDF", "DOCX"), true);
        when(templateService.get(eq("user-1"), eq("modern-ats"))).thenReturn(modern);

        mockMvc.perform(get("/api/resumes/templates/modern-ats").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value("modern-ats"));
    }

    @Test
    void getReturns404ForAnUnknownTemplate() throws Exception {
        when(templateService.get(eq("user-1"), eq("nope"))).thenThrow(ApiException.notOwned());

        mockMvc.perform(get("/api/resumes/templates/nope").header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRequiresTheCallerIdHeader() throws Exception {
        mockMvc.perform(get("/api/resumes/templates/classic"))
                .andExpect(status().isUnauthorized());
    }
}
