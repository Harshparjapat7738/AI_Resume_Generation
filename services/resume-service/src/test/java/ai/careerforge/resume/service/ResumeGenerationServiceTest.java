package ai.careerforge.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.resume.client.AiServiceClient;
import ai.careerforge.resume.client.ClientDtos.EvidenceItem;
import ai.careerforge.resume.client.ClientDtos.EvidenceSelectionResponse;
import ai.careerforge.resume.client.ClientDtos.JdAnalysis;
import ai.careerforge.resume.client.ClientDtos.Requirement;
import ai.careerforge.resume.client.ClientDtos.ResumeContentResponse;
import ai.careerforge.resume.client.JdServiceClient;
import ai.careerforge.resume.client.ProfileServiceClient;
import ai.careerforge.resume.domain.ResumeGeneration;
import ai.careerforge.resume.domain.ResumeVersion;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateSource;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.repository.ResumeGenerationRepository;
import ai.careerforge.resume.repository.ResumeVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers "template selection", "persistence" and "generation uses selected template" (a
 * resolved template is stamped on both persisted documents), "existing resume generation"
 * (no templateId supplied still succeeds, defaulting to classic) and "unauthorized template
 * access" (an invalid/foreign template short-circuits before any AI call) from the template
 * system task's test list.
 */
@ExtendWith(MockitoExtension.class)
class ResumeGenerationServiceTest {

    @Mock
    private ProfileServiceClient profileServiceClient;
    @Mock
    private JdServiceClient jdServiceClient;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private ResumeGenerationRepository generations;
    @Mock
    private ResumeVersionRepository versions;
    @Mock
    private TemplateService templateService;

    private ResumeGenerationService service;

    private static final String USER_ID = "user-1";
    private static final String JD_ID = "jd-1";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new ResumeGenerationService(profileServiceClient, jdServiceClient, aiServiceClient,
                generations, versions, templateService, objectMapper);
    }

    private static Template template(String id) {
        return new Template(id, "Name", "desc", id, TemplateType.RESUME, "1", TemplateStatus.ACTIVE,
                TemplateSource.BUILT_IN, null, List.of("PDF", "DOCX"), true);
    }

    private void stubHappyPathUpstreams() throws Exception {
        JdAnalysis analysis = new JdAnalysis(JD_ID, "Engineer", "Acme", "MID",
                List.of("java"), List.of(new Requirement("REQ-001", "Java experience", "HARD_REQUIRED", 10, List.of("java"))));
        when(jdServiceClient.getAnalysis(JD_ID)).thenReturn(analysis);

        EvidenceItem evidence = new EvidenceItem("EXP-001", "EXPERIENCE", "Backend Engineer", "Acme",
                "Built things", List.of("Java"), List.of(), "2020-01", "2023-01");
        when(profileServiceClient.getEvidence()).thenReturn(List.of(evidence));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode selection = objectMapper.readTree(
                "{\"matches\":[{\"requirementId\":\"REQ-001\",\"evidenceIds\":[\"EXP-001\"],"
                        + "\"matchStrength\":\"STRONG\",\"reason\":\"matches\"}]}");
        when(aiServiceClient.selectEvidence(any())).thenReturn(new EvidenceSelectionResponse(selection, null));

        JsonNode content = objectMapper.readTree("{\"summary\":{\"text\":\"Experienced engineer.\"}}");
        JsonNode grounding = objectMapper.readTree("{\"passed\":true,\"violations\":[],\"checkedStatements\":1}");
        JsonNode provenance = objectMapper.readTree("{\"promptVersion\":\"v1\",\"model\":\"llama\"}");
        when(aiServiceClient.generateContent(any()))
                .thenReturn(new ResumeContentResponse(content, grounding, List.of(), provenance));

        when(generations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generateStampsTheExplicitlySelectedTemplateOnGenerationAndVersion() throws Exception {
        Template modern = template("modern-ats");
        when(templateService.resolveForGeneration(USER_ID, "modern-ats")).thenReturn(modern);
        stubHappyPathUpstreams();

        ResumeVersion result = service.generate(USER_ID, JD_ID, "modern-ats");

        assertThat(result.templateId()).isEqualTo("modern-ats");
        assertThat(result.templateVersion()).isEqualTo("1");

        var generationCaptor = org.mockito.ArgumentCaptor.forClass(ResumeGeneration.class);
        verify(generations, org.mockito.Mockito.atLeastOnce()).save(generationCaptor.capture());
        assertThat(generationCaptor.getAllValues().get(0).templateId()).isEqualTo("modern-ats");
    }

    @Test
    void generateDefaultsToClassicWhenNoTemplateIdIsSupplied() throws Exception {
        Template classic = template("classic");
        when(templateService.resolveForGeneration(USER_ID, null)).thenReturn(classic);
        stubHappyPathUpstreams();

        ResumeVersion result = service.generate(USER_ID, JD_ID, null);

        assertThat(result.templateId()).isEqualTo("classic");
    }

    @Test
    void generateRejectsAnUnauthorizedOrUnknownTemplateBeforeAnyAiCall() {
        when(templateService.resolveForGeneration(USER_ID, "someone-elses-upload"))
                .thenThrow(ApiException.notOwned());

        assertThatThrownBy(() -> service.generate(USER_ID, JD_ID, "someone-elses-upload"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(jdServiceClient, profileServiceClient, aiServiceClient);
        verify(generations, never()).save(any());
    }
}
