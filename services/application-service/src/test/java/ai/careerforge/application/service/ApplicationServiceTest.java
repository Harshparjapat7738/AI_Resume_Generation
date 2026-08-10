package ai.careerforge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.careerforge.application.client.AssessmentServiceClient;
import ai.careerforge.application.client.ClientDtos.AssessmentDto;
import ai.careerforge.application.client.ClientDtos.JobDescriptionDto;
import ai.careerforge.application.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.application.client.ClientDtos.TemplateDto;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ResumeServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.ApplicationStatus;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.ApplicationRepository;
import ai.careerforge.application.repository.ApplicationStatusHistoryRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the parts of {@link ApplicationService} that are actual decisions, not
 * pass-throughs: status derivation on create/attach, ownership propagation (jd-service and
 * resume-service 404s both become {@code notOwned}), the JD/resume cross-check, the
 * best-effort assessment lookup, and legal status transitions.
 */
class ApplicationServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JD_ID = "jd-1";
    private static final String RESUME_ID = "resume-1";

    private JdServiceClient jdServiceClient;
    private ResumeServiceClient resumeServiceClient;
    private AssessmentServiceClient assessmentServiceClient;
    private ApplicationRepository applications;
    private ApplicationStatusHistoryRepository history;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        jdServiceClient = mock(JdServiceClient.class);
        resumeServiceClient = mock(ResumeServiceClient.class);
        assessmentServiceClient = mock(AssessmentServiceClient.class);
        applications = mock(ApplicationRepository.class);
        history = mock(ApplicationStatusHistoryRepository.class);
        service = new ApplicationService(
                jdServiceClient, resumeServiceClient, assessmentServiceClient, applications, history);

        // Mongo would normally assign this; the repository mock echoes back what it's given.
        when(applications.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        when(jdServiceClient.get(JD_ID)).thenReturn(new JobDescriptionDto(JD_ID, "CONFIRMED", "Backend Engineer", "Acme"));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void createsInDraftWhenNoResumeIsSupplied() {
            Application application = service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, null, null);

            assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
            assertThat(application.jobTitle()).isEqualTo("Backend Engineer");
            assertThat(application.company()).isEqualTo("Acme");
            verifyNoInteractions(resumeServiceClient, assessmentServiceClient);
        }

        @Test
        void resumeOnlyWithAttachedResumeCompletesImmediately() {
            when(resumeServiceClient.getResume(RESUME_ID))
                    .thenReturn(new ResumeVersionDto(RESUME_ID, JD_ID, "Backend Engineer", "Acme"));
            when(assessmentServiceClient.get(RESUME_ID)).thenReturn(new AssessmentDto(RESUME_ID));

            Application application = service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, "modern-ats", RESUME_ID);

            assertThat(application.status()).isEqualTo(ApplicationStatus.COMPLETED);
            assertThat(application.resumeVersionId()).isEqualTo(RESUME_ID);
            assertThat(application.assessed()).isTrue();
        }

        @Test
        void nonResumeOnlyTypeStopsAtProcessingEvenWithAResumeAttached() {
            when(resumeServiceClient.getResume(RESUME_ID))
                    .thenReturn(new ResumeVersionDto(RESUME_ID, JD_ID, "Backend Engineer", "Acme"));
            when(assessmentServiceClient.get(RESUME_ID)).thenReturn(new AssessmentDto(RESUME_ID));

            Application application = service.create(USER_ID, JD_ID, GenerationType.ALL, null, RESUME_ID);

            assertThat(application.status()).isEqualTo(ApplicationStatus.PROCESSING);
        }

        @Test
        void representableButUnimplementedTypesCanStillBeCreated() {
            Application application = service.create(USER_ID, JD_ID, GenerationType.COVER_LETTER_ONLY, null, null);

            assertThat(application.generationType()).isEqualTo(GenerationType.COVER_LETTER_ONLY);
            assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
        }

        @Test
        void missingAssessmentIsNonFatal() {
            when(resumeServiceClient.getResume(RESUME_ID))
                    .thenReturn(new ResumeVersionDto(RESUME_ID, JD_ID, "Backend Engineer", "Acme"));
            when(assessmentServiceClient.get(RESUME_ID)).thenThrow(notFound());

            Application application = service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, null, RESUME_ID);

            assertThat(application.status()).isEqualTo(ApplicationStatus.COMPLETED);
            assertThat(application.assessed()).isFalse();
        }

        @Test
        void jobDescriptionNotOwnedByCallerIsReportedAsNotFound() {
            when(jdServiceClient.get(JD_ID)).thenThrow(notFound());

            assertThatThrownBy(() -> service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void validTemplateIdIsAcceptedAndCarried() {
            when(resumeServiceClient.getTemplate("modern-ats")).thenReturn(new TemplateDto("modern-ats"));

            Application application = service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, "modern-ats", null);

            assertThat(application.templateId()).isEqualTo("modern-ats");
        }

        @Test
        void unknownOrUnownedTemplateIsRejected() {
            when(resumeServiceClient.getTemplate("someone-elses")).thenThrow(notFound());

            assertThatThrownBy(() -> service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, "someone-elses", null))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void resumeFromADifferentJobDescriptionIsRejected() {
            when(resumeServiceClient.getResume(RESUME_ID))
                    .thenReturn(new ResumeVersionDto(RESUME_ID, "some-other-jd", "Other Title", "Other Co"));

            assertThatThrownBy(() -> service.create(USER_ID, JD_ID, GenerationType.RESUME_ONLY, null, RESUME_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        void draftToProcessingIsAllowed() {
            Application application = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                    GenerationType.ALL, null);
            when(applications.findByIdAndUserId("app-1", USER_ID)).thenReturn(Optional.of(application));

            Application result = service.updateStatus(USER_ID, "app-1", ApplicationStatus.PROCESSING, null);

            assertThat(result.status()).isEqualTo(ApplicationStatus.PROCESSING);
        }

        @Test
        void completedIsTerminal() {
            Application application = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                    GenerationType.RESUME_ONLY, null);
            application.attachResume(RESUME_ID, true); // -> COMPLETED
            when(applications.findByIdAndUserId("app-1", USER_ID)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> service.updateStatus(USER_ID, "app-1", ApplicationStatus.PROCESSING, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.CONFLICT);
        }

        @Test
        void failedRecordsTheFailureCodeFromTheNote() {
            Application application = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                    GenerationType.RESUME_ONLY, null);
            application.transitionTo(ApplicationStatus.PROCESSING);
            when(applications.findByIdAndUserId("app-1", USER_ID)).thenReturn(Optional.of(application));

            Application result = service.updateStatus(USER_ID, "app-1", ApplicationStatus.FAILED, "AI_GENERATION_FAILED");

            assertThat(result.status()).isEqualTo(ApplicationStatus.FAILED);
            assertThat(result.failureCode()).isEqualTo("AI_GENERATION_FAILED");
        }
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(HttpMethod.GET, "/x", java.util.Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, null);
    }
}
