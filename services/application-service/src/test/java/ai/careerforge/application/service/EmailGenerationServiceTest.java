package ai.careerforge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.careerforge.application.client.AiServiceClient;
import ai.careerforge.application.client.AssessmentServiceClient;
import ai.careerforge.application.client.ClientDtos.EmailContentResponse;
import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.application.client.ClientDtos.ProfileDto;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.client.ResumeServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.EmailContent;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.ApplicationRepository;
import ai.careerforge.application.repository.ApplicationStatusHistoryRepository;
import ai.careerforge.application.repository.EmailContentRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the decisions {@link EmailGenerationService} actually makes: the two guards
 * (generation type, confirmed job title), the evidence-required rule, deterministic
 * subject/signature assembly from verified data only, graceful fallback when the model's
 * paragraphs were removed by the grounding degrade path, versioning across regenerations, and
 * that the application is repointed at the new email.
 *
 * <p>{@link ApplicationService} is used as a real collaborator (backed by a mocked
 * {@link ApplicationRepository}) rather than mocked directly — Mockito's inline mock maker
 * cannot instrument concrete classes on this JDK, and mocking it would test nothing that
 * mocking its own dependencies doesn't already cover.
 */
class EmailGenerationServiceTest {

    private static final String USER_ID = "user-1";
    private static final String APP_ID = "app-1";

    private ApplicationRepository applications;
    private ApplicationService applicationService;
    private ProfileServiceClient profileServiceClient;
    private AiServiceClient aiServiceClient;
    private EmailContentRepository emailContents;
    private EmailGenerationService service;

    private Application application;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        applicationService = new ApplicationService(
                mock(JdServiceClient.class), mock(ResumeServiceClient.class), mock(AssessmentServiceClient.class),
                applications, mock(ApplicationStatusHistoryRepository.class));

        profileServiceClient = mock(ProfileServiceClient.class);
        aiServiceClient = mock(AiServiceClient.class);
        emailContents = mock(EmailContentRepository.class);
        service = new EmailGenerationService(
                applicationService, profileServiceClient, aiServiceClient, emailContents, new ObjectMapper());

        application = new Application(USER_ID, "jd-1", "Backend Engineer", "Acme",
                GenerationType.EMAIL_ONLY, null);
        when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(application));
        when(applications.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        // Real Spring Data assigns @Id on save; the mocked repository must simulate that or
        // Application.attachEmail(saved.id()) would be called with null.
        AtomicInteger idSequence = new AtomicInteger();
        when(emailContents.save(any(EmailContent.class))).thenAnswer(inv ->
                withId(inv.getArgument(0), "email-" + idSequence.incrementAndGet()));
        when(emailContents.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                .thenReturn(Optional.empty());
    }

    private static EmailContent withId(EmailContent content, String id) {
        try {
            Field field = EmailContent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(content, id);
            return content;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private EvidenceItem sampleEvidence() {
        return new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Initech",
                "Built services.", List.of("Java"), List.of("1500 users"), "2021", "Present");
    }

    private EmailContentResponse aiResponse(String bodyText, String closingText) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "content": {
                    "greeting": "Dear Hiring Manager,",
                    "bodyParagraph": { "text": "%s", "evidenceIds": ["EXP-004"] },
                    "closingParagraph": { "text": "%s", "evidenceIds": ["EXP-004"] },
                    "signOff": "Sincerely,"
                  },
                  "grounding": { "passed": true, "violations": [], "checkedStatements": 2 },
                  "removedParagraphs": [],
                  "provenance": { "promptVersion": "email-content@v1", "model": "test-model",
                                   "totalTokens": 42, "regenerated": false }
                }""".formatted(bodyText, closingText);
        return mapper.readValue(json, EmailContentResponse.class);
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        void rejectsGenerationTypesOtherThanEmailOnly() {
            Application resumeOnly = new Application(USER_ID, "jd-1", "Backend Engineer", "Acme",
                    GenerationType.RESUME_ONLY, null);
            when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(resumeOnly));

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verifyNoInteractions(profileServiceClient, aiServiceClient);
        }

        @Test
        void rejectsAnApplicationWithNoConfirmedJobTitle() {
            Application noTitle = new Application(USER_ID, "jd-1", null, null,
                    GenerationType.EMAIL_ONLY, null);
            when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(noTitle));

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verifyNoInteractions(aiServiceClient);
        }

        @Test
        void rejectsWhenTheCandidateHasNoEvidence() {
            when(profileServiceClient.getEvidence()).thenReturn(List.of());

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verifyNoInteractions(aiServiceClient);
        }

        @Test
        void doesNotOwnAnApplicationBelongingToAnotherUser() {
            when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("generation")
    class Generation {

        @Test
        void subjectIsBuiltDeterministicallyFromTheApplicationsJobAndCompany() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto("Jane Doe")));
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.subject()).isEqualTo("Application for Backend Engineer at Acme - Jane Doe");
        }

        @Test
        void bodyIncludesTheGroundedParagraphsAndTheCandidatesRealSignature() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto("Jane Doe")));
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.body())
                    .contains("Dear Hiring Manager,")
                    .contains("I have led backend systems.")
                    .contains("My resume is attached.")
                    .contains("Sincerely,")
                    .endsWith("Jane Doe");
        }

        @Test
        void missingCandidateNameOmitsTheSignatureRatherThanInventingOne() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto(null)));
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.subject()).doesNotContain(" - ");
            assertThat(result.body()).doesNotContainPattern("Sincerely,\\n\\w");
        }

        @Test
        @DisplayName("a paragraph removed by the grounding degrade path falls back to a deterministic, trusted sentence")
        void fallsBackWhenAParagraphWasRemoved() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto("Jane Doe")));
            ObjectMapper mapper = new ObjectMapper();
            String json = """
                    {
                      "content": {
                        "greeting": "Dear Hiring Manager,",
                        "closingParagraph": { "text": "My resume is attached.", "evidenceIds": ["EXP-004"] },
                        "signOff": "Sincerely,"
                      },
                      "grounding": { "passed": false, "violations": [], "checkedStatements": 1 },
                      "removedParagraphs": ["bodyParagraph"],
                      "provenance": { "promptVersion": "email-content@v1", "model": "test-model",
                                       "totalTokens": 42, "regenerated": true }
                    }""";
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(mapper.readValue(json, EmailContentResponse.class));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.body()).contains("I am writing to express my interest in the Backend Engineer role at Acme.");
            assertThat(result.removedParagraphs()).containsExactly("bodyParagraph");
        }

        @Test
        void attachesTheNewEmailToTheApplication() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto("Jane Doe")));
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(application.emailId()).isEqualTo(result.id());
            assertThat(application.status().name()).isEqualTo("COMPLETED");
        }

        @Test
        void regeneratingIncrementsTheVersionAndKeepsThePreviousOneUntouched() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile())
                    .thenReturn(new ProfileDto(new PersonalInformationDto("Jane Doe")));
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent existing = new EmailContent(APP_ID, USER_ID, 1, "old subject", "old body",
                    List.of(), Map.of(), List.of(), "email-content@v1", "test-model");
            when(emailContents.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                    .thenReturn(Optional.of(existing));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.version()).isEqualTo(2);
            assertThat(existing.body()).isEqualTo("old body"); // untouched — immutable version
        }

        @Test
        void missingProfileNameIsNonFatal() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(profileServiceClient.getProfile()).thenThrow(notFound());
            when(aiServiceClient.generateEmailContent(any()))
                    .thenReturn(aiResponse("I have led backend systems.", "My resume is attached."));

            EmailContent result = service.generate(USER_ID, APP_ID);

            assertThat(result.subject()).doesNotContain(" - Jane");
        }

    }

    @Nested
    @DisplayName("requireLatest")
    class RequireLatest {

        @Test
        void returns404WhenNoEmailHasEverBeenGenerated() {
            assertThatThrownBy(() -> service.requireLatest(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void returnsTheLatestVersionWhenOneExists() {
            EmailContent existing = new EmailContent(APP_ID, USER_ID, 3, "subject", "body",
                    List.of(), Map.of(), List.of(), "email-content@v1", "test-model");
            when(emailContents.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                    .thenReturn(Optional.of(existing));

            assertThat(service.requireLatest(USER_ID, APP_ID).version()).isEqualTo(3);
        }
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, null);
    }
}
