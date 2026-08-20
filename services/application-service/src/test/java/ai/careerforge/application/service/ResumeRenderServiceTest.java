package ai.careerforge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.careerforge.application.client.AssessmentServiceClient;
import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.application.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.application.client.ClientDtos.ProfileDto;
import ai.careerforge.application.client.ClientDtos.RenderDocumentMetadata;
import ai.careerforge.application.client.ClientDtos.RenderErrorDto;
import ai.careerforge.application.client.ClientDtos.RenderResponse;
import ai.careerforge.application.client.ClientDtos.ResumeRenderRequest;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.client.RenderServiceClient;
import ai.careerforge.application.client.ResumeServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.ApplicationRepository;
import ai.careerforge.application.repository.ApplicationStatusHistoryRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the complete flow this class connects: JD → JD optimization data
 * (jd-service) → render-service → PDF (ADR-036/ADR-033), including all three failure categories
 * ADR-036's contract distinguishes. {@link ApplicationService} is a real collaborator backed by
 * a mocked {@link ApplicationRepository} rather than mocked directly — Mockito's inline mock
 * maker cannot instrument concrete classes on this JDK, matching
 * {@link EmailGenerationServiceTest}'s own documented reasoning for the same pattern.
 */
class ResumeRenderServiceTest {

    private static final String USER_ID = "user-1";
    private static final String APP_ID = "app-1";
    private static final String JD_ID = "jd-1";

    private ApplicationRepository applications;
    private ApplicationService applicationService;
    private JdServiceClient jdServiceClient;
    private ProfileServiceClient profileServiceClient;
    private RenderServiceClient renderServiceClient;
    private ResumeRenderService service;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        jdServiceClient = mock(JdServiceClient.class);
        profileServiceClient = mock(ProfileServiceClient.class);
        renderServiceClient = mock(RenderServiceClient.class);

        applicationService = new ApplicationService(jdServiceClient, mock(ResumeServiceClient.class),
                mock(AssessmentServiceClient.class), applications, mock(ApplicationStatusHistoryRepository.class));
        service = new ResumeRenderService(applicationService, jdServiceClient, profileServiceClient, renderServiceClient);

        Application application = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                GenerationType.RESUME_ONLY, null);
        when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(application));
    }

    private static PersonalInformationDto personalInfo() {
        return new PersonalInformationDto("Jane Doe", "jane@example.com", "+1 555 0100",
                List.of("https://linkedin.com/in/janedoe"));
    }

    private static EvidenceItem experience(String evidenceId, boolean withDescription) {
        return new EvidenceItem(evidenceId, "EXPERIENCE", "Senior Backend Engineer at Acme", "Acme",
                withDescription ? "Built and shipped a payments service." : null,
                List.of("Java"), List.of(), "2022-01", "Present");
    }

    private static EvidenceItem skill(String evidenceId) {
        return new EvidenceItem(evidenceId, "SKILL", "Kafka", null, "Advanced", List.of("Kafka"), List.of(), null, null);
    }

    private static JdOptimizationDto optimization(List<String> citedEvidenceIds, Map<String, Object> optimisation) {
        return new JdOptimizationDto("opt-1", JD_ID, optimisation, citedEvidenceIds, Instant.parse("2026-08-20T10:00:00Z"));
    }

    private static Map<String, Object> optimisationWithEmphasis(String... rankedEvidenceIdsInOrder) {
        List<Map<String, Object>> emphasis = new java.util.ArrayList<>();
        for (int i = 0; i < rankedEvidenceIdsInOrder.length; i++) {
            emphasis.add(Map.of("evidenceId", rankedEvidenceIdsInOrder[i], "rank", i + 1));
        }
        return Map.of("emphasis", emphasis);
    }

    private static RenderResponse succeededResponse() {
        RenderDocumentMetadata metadata = new RenderDocumentMetadata("doc-1", "PDF", 4, 1,
                Instant.parse("2026-08-20T10:05:00Z"));
        return new RenderResponse("SUCCEEDED", metadata, "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), List.of());
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, null);
    }

    private static FeignException.BadRequest badRequest() {
        Request request = Request.create(HttpMethod.POST, "/internal/render/resume", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.BadRequest("bad request", request, null, null);
    }

    private static FeignException.ServiceUnavailable serviceUnavailable() {
        Request request = Request.create(HttpMethod.POST, "/internal/render/resume", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("unavailable", request, null, null);
    }

    @Nested
    @DisplayName("successful JD optimization -> render-service -> PDF")
    class SuccessfulRender {

        @Test
        @DisplayName("assembles the resume from cited evidence and returns the rendered PDF bytes")
        void rendersResumeFromCitedEvidence() {
            when(jdServiceClient.getOptimization(JD_ID))
                    .thenReturn(optimization(List.of("EXP-001", "SKILL-001"), optimisationWithEmphasis("EXP-001")));
            when(profileServiceClient.getEvidence())
                    .thenReturn(List.of(experience("EXP-001", true), skill("SKILL-001"), experience("EXP-002", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenReturn(succeededResponse());

            byte[] pdf = service.renderResumePdf(USER_ID, APP_ID);

            assertThat(new String(pdf, StandardCharsets.US_ASCII)).startsWith("%PDF-");
        }

        @Test
        @DisplayName("only evidence the optimization cites is sent — an uncited item never reaches render-service")
        void onlyCitedEvidenceIsSent() {
            when(jdServiceClient.getOptimization(JD_ID))
                    .thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence())
                    .thenReturn(List.of(experience("EXP-001", true), experience("EXP-002", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenReturn(succeededResponse());

            service.renderResumePdf(USER_ID, APP_ID);

            org.mockito.ArgumentCaptor<ResumeRenderRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(ResumeRenderRequest.class);
            org.mockito.Mockito.verify(renderServiceClient).renderResume(captor.capture());
            ResumeRenderRequest sent = captor.getValue();

            assertThat(sent.sections()).hasSize(1);
            assertThat(sent.sections().get(0).heading()).isEqualTo("EXPERIENCE");
            assertThat(sent.sections().get(0).entries()).hasSize(1);
            assertThat(sent.sections().get(0).entries().get(0).evidenceId()).isEqualTo("EXP-001");
            assertThat(sent.header().fullName()).isEqualTo("Jane Doe");
            assertThat(sent.header().email()).isEqualTo("jane@example.com");
            assertThat(sent.template()).isEqualTo("STANDARD");
            assertThat(sent.outputFormat()).isEqualTo("PDF");
        }

        @Test
        @DisplayName("emphasis ranking orders entries within a section")
        void emphasisRankingOrdersEntries() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(
                    List.of("EXP-001", "EXP-002"), optimisationWithEmphasis("EXP-002", "EXP-001")));
            when(profileServiceClient.getEvidence())
                    .thenReturn(List.of(experience("EXP-001", true), experience("EXP-002", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenReturn(succeededResponse());

            service.renderResumePdf(USER_ID, APP_ID);

            org.mockito.ArgumentCaptor<ResumeRenderRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(ResumeRenderRequest.class);
            org.mockito.Mockito.verify(renderServiceClient).renderResume(captor.capture());
            List<String> orderedIds = captor.getValue().sections().get(0).entries().stream()
                    .map(e -> e.evidenceId()).toList();

            assertThat(orderedIds).containsExactly("EXP-002", "EXP-001");
        }

        @Test
        @DisplayName("an evidence item with no description produces an entry with no bullets, not a fabricated one")
        void entryWithoutDescriptionHasNoBullets() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", false)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenReturn(succeededResponse());

            service.renderResumePdf(USER_ID, APP_ID);

            org.mockito.ArgumentCaptor<ResumeRenderRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(ResumeRenderRequest.class);
            org.mockito.Mockito.verify(renderServiceClient).renderResume(captor.capture());

            assertThat(captor.getValue().sections().get(0).entries().get(0).bullets()).isEmpty();
        }
    }

    @Nested
    @DisplayName("render-service unavailable")
    class RenderServiceUnavailable {

        @Test
        @DisplayName("a connectivity/5xx failure from render-service maps to UPSTREAM_UNAVAILABLE")
        void serviceUnavailableMapsToUpstreamUnavailable() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenThrow(serviceUnavailable());

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        }

        @Test
        @DisplayName("profile-service being unreachable also maps to UPSTREAM_UNAVAILABLE")
        void profileServiceUnreachableMapsToUpstreamUnavailable() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenThrow(serviceUnavailable());

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("invalid render request")
    class InvalidRenderRequest {

        @Test
        @DisplayName("render-service rejecting the assembled request (400) maps to VALIDATION_ERROR")
        void renderServiceBadRequestMapsToValidationError() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            when(renderServiceClient.renderResume(any())).thenThrow(badRequest());

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("no JD optimization yet (404) is a validation problem, not an unavailable one")
        void missingOptimizationMapsToValidationError() {
            when(jdServiceClient.getOptimization(JD_ID)).thenThrow(notFound());

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("an optimization that cites none of the candidate's evidence is rejected before calling render-service")
        void noCitedEvidenceIsRejectedLocally() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-999"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            org.mockito.Mockito.verifyNoInteractions(renderServiceClient);
        }

        @Test
        @DisplayName("a profile missing name or email is rejected before calling render-service")
        void incompleteProfileIsRejectedLocally() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto(null, null, null, List.of())));

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            org.mockito.Mockito.verifyNoInteractions(renderServiceClient);
        }
    }

    @Nested
    @DisplayName("rendering failure")
    class RenderingFailure {

        @Test
        @DisplayName("render-service reachable but reporting FAILED maps to DOCUMENT_RENDER_FAILED")
        void renderServiceFailedStatusMapsToDocumentRenderFailed() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            RenderResponse failed = new RenderResponse("FAILED", null, null,
                    List.of(new RenderErrorDto("PDF_CONVERSION_FAILED", "conversion failed")));
            when(renderServiceClient.renderResume(any())).thenReturn(failed);

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.DOCUMENT_RENDER_FAILED);
        }

        @Test
        @DisplayName("a SUCCEEDED status with no actual bytes is still treated as a rendering failure")
        void succeededWithNoBytesMapsToDocumentRenderFailed() {
            when(jdServiceClient.getOptimization(JD_ID)).thenReturn(optimization(List.of("EXP-001"), Map.of()));
            when(profileServiceClient.getEvidence()).thenReturn(List.of(experience("EXP-001", true)));
            when(profileServiceClient.getProfile()).thenReturn(new ProfileDto(personalInfo()));
            RenderResponse emptyBytes = new RenderResponse("SUCCEEDED",
                    new RenderDocumentMetadata("doc-1", "PDF", 0, 1, Instant.now()), new byte[0], List.of());
            when(renderServiceClient.renderResume(any())).thenReturn(emptyBytes);

            assertThatThrownBy(() -> service.renderResumePdf(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.DOCUMENT_RENDER_FAILED);
        }
    }

    @Test
    @DisplayName("an application belonging to another user is reported as not found")
    void applicationNotOwnedIsReportedAsNotFound() {
        when(applications.findByIdAndUserId(APP_ID, "someone-else")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renderResumePdf("someone-else", APP_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
