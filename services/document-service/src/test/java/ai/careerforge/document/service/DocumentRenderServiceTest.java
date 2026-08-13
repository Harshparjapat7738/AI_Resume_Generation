package ai.careerforge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ProfileServiceClient;
import ai.careerforge.document.client.ResumeServiceClient;
import ai.careerforge.document.client.TemplateServiceClient;
import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.DocumentType;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.render.PdfRenderer;
import ai.careerforge.document.render.PdfRenderer.RenderedPdf;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
import ai.careerforge.document.repository.RenderedDocumentRepository;
import ai.careerforge.document.template.ResumeTemplate;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentRenderServiceTest {

    @Mock private ResumeServiceClient resumeServiceClient;
    @Mock private ProfileServiceClient profileServiceClient;
    @Mock private TemplateServiceClient templateServiceClient;
    @Mock private PdfRenderer pdfRenderer;
    @Mock private ObjectStorageService storage;
    @Mock private RenderedDocumentRepository documents;
    @Mock private CustomTemplateAssetService customTemplateAssets;

    private DocumentRenderService service;

    private static final String USER_ID = "user-1";
    private static final String RESUME_ID = "resume-1";

    @BeforeEach
    void setUp() {
        // Every existing test here renders a built-in template; customTemplateAssets.findOwned
        // is left unstubbed, which Mockito's default answer resolves to Optional.empty() for an
        // Optional-returning method — i.e. "not a custom template", falling through to exactly
        // the built-in path these tests already exercise. See CustomTemplateAssetServiceTest and
        // DocumentRenderServiceCustomTemplateTest for the ADR-023 dispatch itself.
        service = new DocumentRenderService(resumeServiceClient, profileServiceClient, templateServiceClient,
                new ResumeRenderModelBuilder(), pdfRenderer, storage, documents, customTemplateAssets);
    }

    @Test
    void resumeOwnedBySomeoneElseIs404NeverAnUpstreamLeak() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/resumes/" + RESUME_ID,
                Map.of(), null, new RequestTemplate());
        when(resumeServiceClient.getResume(RESUME_ID)).thenThrow(
                new FeignException.NotFound("not found", request, null, null));

        assertThatThrownBy(() -> service.renderPdf(USER_ID, RESUME_ID, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void withNoExplicitTemplateItRendersWithTheTemplateSelectedAtGenerationTime() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("professional"));
        when(profileServiceClient.getProfile()).thenReturn(emptyProfile());
        when(pdfRenderer.render(any(), any())).thenReturn(new RenderedPdf("pdf-bytes".getBytes(StandardCharsets.UTF_8), 1));
        when(storage.upload(any(), any())).thenReturn("object-key-1");
        when(storage.bucket()).thenReturn("careerforge-documents");
        when(documents.findByResumeVersionIdAndFormatAndUserId(RESUME_ID, DocumentFormat.PDF, USER_ID))
                .thenReturn(Optional.empty());
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.renderPdf(USER_ID, RESUME_ID, null);

        ArgumentCaptor<ResumeTemplate> templateCaptor = ArgumentCaptor.forClass(ResumeTemplate.class);
        verify(pdfRenderer).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo(ResumeTemplate.PROFESSIONAL);
    }

    @Test
    void anExplicitTemplateOverridesTheOneSelectedAtGenerationTime() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("classic"));
        when(profileServiceClient.getProfile()).thenReturn(emptyProfile());
        when(pdfRenderer.render(any(), any())).thenReturn(new RenderedPdf("pdf-bytes".getBytes(StandardCharsets.UTF_8), 1));
        when(storage.upload(any(), any())).thenReturn("object-key-1");
        when(storage.bucket()).thenReturn("careerforge-documents");
        when(documents.findByResumeVersionIdAndFormatAndUserId(RESUME_ID, DocumentFormat.PDF, USER_ID))
                .thenReturn(Optional.empty());
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.renderPdf(USER_ID, RESUME_ID, "modern-ats");

        ArgumentCaptor<ResumeTemplate> templateCaptor = ArgumentCaptor.forClass(ResumeTemplate.class);
        verify(pdfRenderer).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo(ResumeTemplate.MODERN_ATS);
    }

    @Test
    void reRenderingWithIdenticalContentAndTemplateIsIdempotentAndSkipsReupload() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("classic"));
        when(profileServiceClient.getProfile()).thenReturn(emptyProfile());
        byte[] sameBytes = "identical-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(pdfRenderer.render(any(), any())).thenReturn(new RenderedPdf(sameBytes, 1));

        RenderedDocument existing = new RenderedDocument(
                USER_ID, RESUME_ID, DocumentType.RESUME, DocumentFormat.PDF, "existing-key", "bucket",
                sha256(sameBytes), sameBytes.length, 1, "classic", "1", PdfRenderer.ENGINE_VERSION);
        when(documents.findByResumeVersionIdAndFormatAndUserId(RESUME_ID, DocumentFormat.PDF, USER_ID))
                .thenReturn(Optional.of(existing));

        RenderedDocument result = service.renderPdf(USER_ID, RESUME_ID, null);

        assertThat(result).isSameAs(existing);
        verify(storage, never()).upload(any(), any());
        verify(documents, never()).save(any());
    }

    @Test
    void reRenderingWithADifferentTemplateReplacesTheStoredArtifactAndDeletesTheOldOne() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("classic"));
        when(profileServiceClient.getProfile()).thenReturn(emptyProfile());
        when(pdfRenderer.render(any(), any())).thenReturn(new RenderedPdf("new-bytes".getBytes(StandardCharsets.UTF_8), 2));
        when(storage.upload(any(), any())).thenReturn("new-object-key");
        when(storage.bucket()).thenReturn("careerforge-documents");
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RenderedDocument existing = new RenderedDocument(
                USER_ID, RESUME_ID, DocumentType.RESUME, DocumentFormat.PDF, "old-object-key", "bucket",
                "old-sha", 10, 1, "classic", "1", PdfRenderer.ENGINE_VERSION);
        when(documents.findByResumeVersionIdAndFormatAndUserId(RESUME_ID, DocumentFormat.PDF, USER_ID))
                .thenReturn(Optional.of(existing));

        RenderedDocument result = service.renderPdf(USER_ID, RESUME_ID, "modern-ats");

        assertThat(result.templateId()).isEqualTo("modern-ats");
        assertThat(result.objectKey()).isEqualTo("new-object-key");
        verify(storage).delete("old-object-key");
        verify(documents, times(1)).save(existing);
    }

    @Test
    void unknownTemplateIdIsRejectedBeforeFetchingTheProfile() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("classic"));

        assertThatThrownBy(() -> service.renderPdf(USER_ID, RESUME_ID, "not-a-real-template"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(profileServiceClient, never()).getProfile();
    }

    private static ResumeVersionDto resume(String templateId) {
        return new ResumeVersionDto(RESUME_ID, "jd-1", "Engineer", "Acme", templateId, "1",
                Map.of(), java.util.List.of(), java.util.List.of(), Map.of(), java.util.List.of(), null);
    }

    private static ProfileDto emptyProfile() {
        return new ProfileDto(
                new PersonalInformationDto("Test User", null, null, null, java.util.List.of()),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of());
    }

    private static String sha256(byte[] bytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
