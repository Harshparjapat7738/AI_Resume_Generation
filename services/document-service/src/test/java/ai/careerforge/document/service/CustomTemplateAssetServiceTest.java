package ai.careerforge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.docx.DocxMailMerge;
import ai.careerforge.document.docx.DocxStructureAnalyzer;
import ai.careerforge.document.docx.DocxStructureAnalyzerTest;
import ai.careerforge.document.domain.CustomTemplateAsset;
import ai.careerforge.document.domain.TemplateFormat;
import ai.careerforge.document.pdf.PdfMailMerge;
import ai.careerforge.document.pdf.PdfStructureAnalyzer;
import ai.careerforge.document.pdf.PdfStructureAnalyzerTest;
import ai.careerforge.document.render.ResumeRenderModelBuilder;
import ai.careerforge.document.repository.CustomTemplateAssetRepository;
import ai.careerforge.document.repository.RenderedDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Upload validation dispatches to the right format (ADR-023) — checking both extension and
 * magic-byte signature, never the extension alone, using real DOCX/PDF bytes and the real
 * analyzers (cheap, deterministic, and a genuine end-to-end check) rather than mocking the
 * parsing itself. Storage/repository collaborators are mocked since persistence isn't what
 * these tests are verifying.
 */
@ExtendWith(MockitoExtension.class)
class CustomTemplateAssetServiceTest {

    @Mock private ObjectStorageService storage;
    @Mock private CustomTemplateAssetRepository assets;
    @Mock private RenderedDocumentRepository documents;
    @Mock private ai.careerforge.document.client.ResumeServiceClient resumeServiceClient;
    @Mock private ai.careerforge.document.client.ProfileServiceClient profileServiceClient;

    private CustomTemplateAssetService service;

    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        service = new CustomTemplateAssetService(
                new DocxStructureAnalyzer(), new DocxMailMerge(),
                new PdfStructureAnalyzer(), new PdfMailMerge(),
                storage, assets, documents, resumeServiceClient, profileServiceClient,
                new ResumeRenderModelBuilder());
    }

    @Test
    void aRealDocxIsAcceptedAndStoredAsFormatDocx() throws Exception {
        byte[] docx = DocxStructureAnalyzerTest.onePagePackage("Name: {{NAME}}");
        when(storage.upload(any(), any())).thenReturn("object-key");
        when(assets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomTemplateAsset asset = service.storeAndAnalyze(USER_ID, docx, "resume.docx");

        assertThat(asset.format()).isEqualTo(TemplateFormat.DOCX);
        assertThat(asset.detectedFields()).extracting(f -> f.token()).containsExactly("NAME");
    }

    @Test
    void aRealPdfWithAPlaceholderIsAcceptedAndStoredAsFormatPdf() throws Exception {
        byte[] pdf = PdfStructureAnalyzerTest.onePagePdf("Name: {{NAME}}");
        when(storage.upload(any(), any())).thenReturn("object-key");
        when(assets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomTemplateAsset asset = service.storeAndAnalyze(USER_ID, pdf, "resume.pdf");

        assertThat(asset.format()).isEqualTo(TemplateFormat.PDF);
        assertThat(asset.detectedFields()).extracting(f -> f.token()).containsExactly("NAME");
    }

    @Test
    void aPdfWithNoPlaceholdersIsRejected() throws Exception {
        byte[] pdf = PdfStructureAnalyzerTest.onePagePdf("Nothing to fill in here.");

        assertThatThrownBy(() -> service.storeAndAnalyze(USER_ID, pdf, "resume.pdf"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void aFileNamedDotPdfThatIsNotReallyAPdfIsRejected_extensionAloneIsNeverTrusted() {
        byte[] notActuallyAPdf = "<html>fake</html>".getBytes();

        assertThatThrownBy(() -> service.storeAndAnalyze(USER_ID, notActuallyAPdf, "resume.pdf"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void aFileNamedDotDocxThatIsNotReallyAZipIsRejected_extensionAloneIsNeverTrusted() {
        byte[] notActuallyAZip = "plain text pretending to be a docx".getBytes();

        assertThatThrownBy(() -> service.storeAndAnalyze(USER_ID, notActuallyAZip, "resume.docx"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void anUnsupportedExtensionIsRejected() {
        assertThatThrownBy(() -> service.storeAndAnalyze(USER_ID, new byte[] {1, 2, 3}, "resume.txt"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void anOversizedFileIsRejectedRegardlessOfFormat() {
        byte[] tooBig = new byte[6 * 1024 * 1024];
        assertThatThrownBy(() -> service.storeAndAnalyze(USER_ID, tooBig, "resume.pdf"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }
}
