package ai.careerforge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.document.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.document.client.ClientDtos.ProfileDto;
import ai.careerforge.document.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.document.client.ClientDtos.TemplateFieldMappingDto;
import ai.careerforge.document.client.ProfileServiceClient;
import ai.careerforge.document.client.ResumeServiceClient;
import ai.careerforge.document.client.TemplateServiceClient;
import ai.careerforge.document.domain.CustomTemplateAsset;
import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.DocumentType;
import ai.careerforge.document.domain.RenderedDocument;
import ai.careerforge.document.domain.TemplateFormat;
import ai.careerforge.document.render.PdfRenderer;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
import ai.careerforge.document.repository.RenderedDocumentRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies the ADR-023 dispatch itself: when {@code templateId} names one of the caller's own
 * custom templates, the *main* render endpoint delegates entirely to
 * {@code CustomTemplateAssetService.generate} — never the built-in {@code PdfRenderer} path —
 * and supplies it the owner's saved field mapping fetched from resume-service. Every
 * collaborator here is mocked; {@code CustomTemplateAssetServiceTest} and
 * {@code pdf.PdfMailMergeTest}/{@code docx.DocxMailMergeTest} cover the real
 * analysis/merge behavior this delegates to.
 */
@ExtendWith(MockitoExtension.class)
class DocumentRenderServiceCustomTemplateTest {

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
    private static final String CUSTOM_TEMPLATE_ID = "custom-template-1";

    @BeforeEach
    void setUp() {
        service = new DocumentRenderService(resumeServiceClient, profileServiceClient, templateServiceClient,
                new ResumeRenderModelBuilder(), pdfRenderer, storage, documents, customTemplateAssets);
    }

    @Test
    void aCustomTemplateIdDelegatesToCustomTemplateAssetServiceNotTheBuiltInRenderer() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume(CUSTOM_TEMPLATE_ID));
        when(customTemplateAssets.findOwned(USER_ID, CUSTOM_TEMPLATE_ID))
                .thenReturn(Optional.of(customAsset()));
        when(templateServiceClient.getTemplate(CUSTOM_TEMPLATE_ID))
                .thenReturn(new TemplateFieldMappingDto(CUSTOM_TEMPLATE_ID, Map.of("NAME", "NAME")));
        RenderedDocument expected = new RenderedDocument(USER_ID, RESUME_ID, DocumentType.RESUME, DocumentFormat.DOCX,
                "key", "bucket", "sha", 10, 0, CUSTOM_TEMPLATE_ID, "custom", "engine");
        when(customTemplateAssets.generate(USER_ID, CUSTOM_TEMPLATE_ID, RESUME_ID, Map.of("NAME", "NAME")))
                .thenReturn(expected);

        RenderedDocument result = service.renderPdf(USER_ID, RESUME_ID, null);

        assertThat(result).isSameAs(expected);
        verify(customTemplateAssets).generate(USER_ID, CUSTOM_TEMPLATE_ID, RESUME_ID, Map.of("NAME", "NAME"));
        verify(pdfRenderer, never()).render(any(), any());
        verify(profileServiceClient, never()).getProfile(); // built-in-only path, never reached
    }

    @Test
    void aBuiltInTemplateIdStillGoesThroughTheOriginalPathWhenNoCustomAssetMatches() {
        when(resumeServiceClient.getResume(RESUME_ID)).thenReturn(resume("classic"));
        when(customTemplateAssets.findOwned(USER_ID, "classic")).thenReturn(Optional.empty());
        when(profileServiceClient.getProfile()).thenReturn(emptyProfile());
        when(pdfRenderer.render(any(), any()))
                .thenReturn(new PdfRenderer.RenderedPdf("bytes".getBytes(), 1));
        when(storage.upload(any(), any())).thenReturn("object-key");
        when(storage.bucket()).thenReturn("bucket");
        when(documents.findByResumeVersionIdAndFormatAndUserId(RESUME_ID, DocumentFormat.PDF, USER_ID))
                .thenReturn(Optional.empty());
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.renderPdf(USER_ID, RESUME_ID, null);

        verify(customTemplateAssets, never()).generate(any(), any(), any(), any());
        verify(pdfRenderer).render(any(), any());
    }

    private static ResumeVersionDto resume(String templateId) {
        return new ResumeVersionDto(RESUME_ID, "jd-1", "Engineer", "Acme", templateId, "1",
                Map.of(), java.util.List.of(), java.util.List.of(), Map.of(), java.util.List.of(), null);
    }

    private static CustomTemplateAsset customAsset() {
        return new CustomTemplateAsset(USER_ID, "resume.docx", TemplateFormat.DOCX, "object-key", 10, "sha",
                null, java.util.List.of());
    }

    private static ProfileDto emptyProfile() {
        return new ProfileDto(
                new PersonalInformationDto("Test User", null, null, null, java.util.List.of()),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of());
    }
}
