package ai.careerforge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.careerforge.application.client.AiServiceClient;
import ai.careerforge.application.client.AssessmentServiceClient;
import ai.careerforge.application.client.ClientDtos.CoverLetterContentResponse;
import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.EvidenceSelectionResponse;
import ai.careerforge.application.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.application.client.ClientDtos.RequirementDto;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.client.ResumeServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.CoverLetterVersion;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.ApplicationRepository;
import ai.careerforge.application.repository.ApplicationStatusHistoryRepository;
import ai.careerforge.application.repository.CoverLetterVersionRepository;
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
 * Covers the decisions {@link CoverLetterGenerationService} actually makes: the generation-type
 * guard, the JD-confirmed/requirements/evidence-required rules, the two-stage AI call (evidence
 * selection then content), versioning across regenerations, and that the application is
 * repointed at the new letter. Mirrors {@link EmailGenerationServiceTest}'s structure.
 *
 * <p>{@link ApplicationService} is used as a real collaborator (backed by a mocked
 * {@link ApplicationRepository}) rather than mocked directly — Mockito's inline mock maker
 * cannot instrument concrete classes on this JDK, and mocking it would test nothing that
 * mocking its own dependencies doesn't already cover.
 */
class CoverLetterGenerationServiceTest {

    private static final String USER_ID = "user-1";
    private static final String APP_ID = "app-1";
    private static final String JD_ID = "jd-1";

    private ApplicationRepository applications;
    private ApplicationService applicationService;
    private ProfileServiceClient profileServiceClient;
    private JdServiceClient jdServiceClient;
    private AiServiceClient aiServiceClient;
    private CoverLetterVersionRepository coverLetterVersions;
    private CoverLetterGenerationService service;

    private Application application;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        applicationService = new ApplicationService(
                mock(JdServiceClient.class), mock(ResumeServiceClient.class), mock(AssessmentServiceClient.class),
                applications, mock(ApplicationStatusHistoryRepository.class));

        profileServiceClient = mock(ProfileServiceClient.class);
        jdServiceClient = mock(JdServiceClient.class);
        aiServiceClient = mock(AiServiceClient.class);
        coverLetterVersions = mock(CoverLetterVersionRepository.class);
        service = new CoverLetterGenerationService(
                applicationService, profileServiceClient, jdServiceClient, aiServiceClient,
                coverLetterVersions, new ObjectMapper());

        application = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                GenerationType.COVER_LETTER_ONLY, null);
        when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(application));
        when(applications.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicInteger idSequence = new AtomicInteger();
        when(coverLetterVersions.save(any(CoverLetterVersion.class))).thenAnswer(inv ->
                withId(inv.getArgument(0), "cl-" + idSequence.incrementAndGet()));
        when(coverLetterVersions.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                .thenReturn(Optional.empty());

        when(jdServiceClient.getAnalysis(JD_ID)).thenReturn(sampleAnalysis());
    }

    private static CoverLetterVersion withId(CoverLetterVersion version, String id) {
        try {
            Field field = CoverLetterVersion.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(version, id);
            return version;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private JdAnalysisDto sampleAnalysis() {
        return new JdAnalysisDto(JD_ID, "Backend Engineer", "Acme", "Senior",
                List.of("java"), List.of(new RequirementDto("REQ-1", "5+ years Java", "SKILL", 10, List.of("java"))));
    }

    private EvidenceItem sampleEvidence() {
        return new EvidenceItem("EXP-004", "EXPERIENCE", "Backend Engineer", "Initech",
                "Built services.", List.of("Java"), List.of("1500 users"), "2021", "Present");
    }

    private EvidenceSelectionResponse selectionResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "selection": { "matches": [
                    { "requirementId": "REQ-1", "evidenceIds": ["EXP-004"], "matchStrength": "STRONG", "reason": "x" }
                  ]},
                  "provenance": { "promptVersion": "evidence-selection@v1", "model": "test-model" }
                }""";
        return mapper.readValue(json, EvidenceSelectionResponse.class);
    }

    private CoverLetterContentResponse coverLetterResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "content": {
                    "greeting": "Dear Hiring Manager,",
                    "openingParagraph": { "text": "I am applying for the Backend Engineer role at Acme.",
                      "evidenceIds": ["EXP-004"] },
                    "bodyParagraphs": [
                      { "text": "I built services at Initech.", "evidenceIds": ["EXP-004"] }
                    ],
                    "closingParagraph": { "text": "I look forward to hearing from you.",
                      "evidenceIds": ["EXP-004"] },
                    "signOff": "Sincerely,"
                  },
                  "grounding": { "passed": true, "violations": [], "checkedStatements": 3 },
                  "removedParagraphs": [],
                  "provenance": { "promptVersion": "cover-letter@v1", "model": "test-model",
                                   "totalTokens": 88, "regenerated": false }
                }""";
        return mapper.readValue(json, CoverLetterContentResponse.class);
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        void rejectsGenerationTypesOtherThanCoverLetterOnly() {
            Application resumeOnly = new Application(USER_ID, JD_ID, "Backend Engineer", "Acme",
                    GenerationType.RESUME_ONLY, null);
            when(applications.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(Optional.of(resumeOnly));

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verifyNoInteractions(profileServiceClient, aiServiceClient);
        }

        @Test
        void rejectsAnUnconfirmedJobDescription() {
            when(jdServiceClient.getAnalysis(JD_ID)).thenThrow(conflict());
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.JD_NOT_CONFIRMED);
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
        void rejectsWhenTheJobDescriptionHasNoExtractedRequirements() {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(jdServiceClient.getAnalysis(JD_ID)).thenReturn(
                    new JdAnalysisDto(JD_ID, "Backend Engineer", "Acme", "Senior", List.of(), List.of()));

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
        void producesAGroundedLetterFromSelectedEvidence() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(aiServiceClient.selectEvidence(any())).thenReturn(selectionResponse());
            when(aiServiceClient.generateCoverLetter(any())).thenReturn(coverLetterResponse());

            CoverLetterVersion result = service.generate(USER_ID, APP_ID);

            assertThat(result.jobTitle()).isEqualTo("Backend Engineer");
            assertThat(result.company()).isEqualTo("Acme");
            assertThat(result.content()).containsKey("openingParagraph");
        }

        @Test
        void rejectsWhenNoEvidenceMatchedAnyRequirement() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            ObjectMapper mapper = new ObjectMapper();
            when(aiServiceClient.selectEvidence(any())).thenReturn(
                    mapper.readValue("""
                            { "selection": { "matches": [] },
                              "provenance": { "promptVersion": "evidence-selection@v1", "model": "test-model" } }""",
                            EvidenceSelectionResponse.class));

            assertThatThrownBy(() -> service.generate(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        void attachesTheNewLetterToTheApplicationAndCompletesIt() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(aiServiceClient.selectEvidence(any())).thenReturn(selectionResponse());
            when(aiServiceClient.generateCoverLetter(any())).thenReturn(coverLetterResponse());

            CoverLetterVersion result = service.generate(USER_ID, APP_ID);

            assertThat(application.coverLetterVersionId()).isEqualTo(result.id());
            assertThat(application.status().name()).isEqualTo("COMPLETED");
        }

        @Test
        void regeneratingIncrementsTheVersionAndKeepsThePreviousOneUntouched() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(aiServiceClient.selectEvidence(any())).thenReturn(selectionResponse());
            when(aiServiceClient.generateCoverLetter(any())).thenReturn(coverLetterResponse());

            CoverLetterVersion existing = new CoverLetterVersion(APP_ID, USER_ID, JD_ID, "Backend Engineer",
                    "Acme", 1, Map.of("greeting", "old"), Map.of(), List.of(), "cover-letter@v1", "test-model");
            when(coverLetterVersions.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                    .thenReturn(Optional.of(existing));

            CoverLetterVersion result = service.generate(USER_ID, APP_ID);

            assertThat(result.version()).isEqualTo(2);
            assertThat(existing.content()).isEqualTo(Map.of("greeting", "old")); // untouched — immutable version
        }

        @Test
        void removedParagraphsFromTheDegradePathAreCarriedThroughForTheUiToDisclose() throws Exception {
            when(profileServiceClient.getEvidence()).thenReturn(List.of(sampleEvidence()));
            when(aiServiceClient.selectEvidence(any())).thenReturn(selectionResponse());
            ObjectMapper mapper = new ObjectMapper();
            String json = """
                    {
                      "content": {
                        "greeting": "Dear Hiring Manager,",
                        "bodyParagraphs": [],
                        "closingParagraph": { "text": "I look forward to hearing from you.",
                          "evidenceIds": ["EXP-004"] },
                        "signOff": "Sincerely,"
                      },
                      "grounding": { "passed": false, "violations": [], "checkedStatements": 1 },
                      "removedParagraphs": ["openingParagraph", "bodyParagraphs[0]"],
                      "provenance": { "promptVersion": "cover-letter@v1", "model": "test-model",
                                       "totalTokens": 88, "regenerated": true }
                    }""";
            when(aiServiceClient.generateCoverLetter(any()))
                    .thenReturn(mapper.readValue(json, CoverLetterContentResponse.class));

            CoverLetterVersion result = service.generate(USER_ID, APP_ID);

            assertThat(result.removedParagraphs()).containsExactly("openingParagraph", "bodyParagraphs[0]");
        }
    }

    @Nested
    @DisplayName("requireLatest")
    class RequireLatest {

        @Test
        void returns404WhenNoLetterHasEverBeenGenerated() {
            assertThatThrownBy(() -> service.requireLatest(USER_ID, APP_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void returnsTheLatestVersionWhenOneExists() {
            CoverLetterVersion existing = new CoverLetterVersion(APP_ID, USER_ID, JD_ID, "Backend Engineer",
                    "Acme", 3, Map.of(), Map.of(), List.of(), "cover-letter@v1", "test-model");
            when(coverLetterVersions.findTopByApplicationIdAndUserIdOrderByVersionDesc(APP_ID, USER_ID))
                    .thenReturn(Optional.of(existing));

            assertThat(service.requireLatest(USER_ID, APP_ID).version()).isEqualTo(3);
        }
    }

    private static FeignException.Conflict conflict() {
        Request request = Request.create(HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.Conflict("conflict", request, null, null);
    }
}
