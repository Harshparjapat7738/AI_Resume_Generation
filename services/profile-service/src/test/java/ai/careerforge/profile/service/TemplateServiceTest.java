package ai.careerforge.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.profile.domain.Template;
import ai.careerforge.profile.domain.TemplateDocumentType;
import ai.careerforge.profile.domain.TemplateFileType;
import ai.careerforge.profile.repository.TemplateRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * "My Templates" (ADR-034) — validation (extension + magic-byte signature, size, filename),
 * ownership (404-not-403, mirroring every other user-scoped resource in this platform),
 * duplicate detection, and the single-default-per-user invariant. Storage itself
 * ({@link ObjectStorageService}) is mocked — this class never touches a real MinIO/S3 endpoint,
 * matching every other unit test in this codebase.
 */
class TemplateServiceTest {

    private static final String USER_ID = "user-1";

    private TemplateRepository templates;
    private ObjectStorageService storage;
    private TemplateService service;

    @BeforeEach
    void setUp() {
        templates = mock(TemplateRepository.class);
        storage = mock(ObjectStorageService.class);
        service = new TemplateService(templates, storage);
    }

    @Nested
    class Upload {

        @Test
        @DisplayName("a real PDF (correct extension + %PDF- signature) is accepted")
        void uploadsAValidPdf() {
            byte[] pdf = pdfBytes();
            when(templates.existsByUserIdAndSha256(eq(USER_ID), anyString())).thenReturn(false);
            when(storage.upload(any(), eq("application/pdf"))).thenReturn("object-key-1");
            when(storage.bucket()).thenReturn("careerforge-templates");
            when(templates.countByUserId(USER_ID)).thenReturn(0L);
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template saved = service.upload(USER_ID, pdf, "resume.pdf", "My Professional Resume", TemplateDocumentType.RESUME);

            assertThat(saved.fileType()).isEqualTo(TemplateFileType.PDF);
            assertThat(saved.name()).isEqualTo("My Professional Resume");
            assertThat(saved.originalFilename()).isEqualTo("resume.pdf");
            assertThat(saved.userId()).isEqualTo(USER_ID);
            verify(storage).upload(pdf, "application/pdf");
        }

        @Test
        @DisplayName("a real DOCX (correct extension + PK zip signature) is accepted")
        void uploadsAValidDocx() {
            byte[] docx = docxBytes();
            when(templates.existsByUserIdAndSha256(eq(USER_ID), anyString())).thenReturn(false);
            when(storage.upload(any(), eq(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                    .thenReturn("object-key-2");
            when(storage.bucket()).thenReturn("careerforge-templates");
            when(templates.countByUserId(USER_ID)).thenReturn(1L);
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template saved = service.upload(USER_ID, docx, "cover-letter.docx", "Cover Letter Template",
                    TemplateDocumentType.COVER_LETTER);

            assertThat(saved.fileType()).isEqualTo(TemplateFileType.DOCX);
            assertThat(saved.documentType()).isEqualTo(TemplateDocumentType.COVER_LETTER);
        }

        @Test
        @DisplayName("the first template a user ever uploads becomes their default automatically")
        void firstUploadBecomesDefault() {
            when(templates.existsByUserIdAndSha256(any(), any())).thenReturn(false);
            when(storage.upload(any(), any())).thenReturn("key");
            when(storage.bucket()).thenReturn("bucket");
            when(templates.countByUserId(USER_ID)).thenReturn(0L);
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template saved = service.upload(USER_ID, pdfBytes(), "resume.pdf", "Resume", TemplateDocumentType.RESUME);

            assertThat(saved.isDefault()).isTrue();
        }

        @Test
        @DisplayName("a second upload is not automatically made default")
        void secondUploadIsNotDefault() {
            when(templates.existsByUserIdAndSha256(any(), any())).thenReturn(false);
            when(storage.upload(any(), any())).thenReturn("key");
            when(storage.bucket()).thenReturn("bucket");
            when(templates.countByUserId(USER_ID)).thenReturn(1L);
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template saved = service.upload(USER_ID, pdfBytes(), "resume.pdf", "Resume", TemplateDocumentType.RESUME);

            assertThat(saved.isDefault()).isFalse();
        }

        @Test
        @DisplayName("falls back to the filename (minus extension) when no name is supplied")
        void fallsBackToFilenameWhenNameIsBlank() {
            when(templates.existsByUserIdAndSha256(any(), any())).thenReturn(false);
            when(storage.upload(any(), any())).thenReturn("key");
            when(storage.bucket()).thenReturn("bucket");
            when(templates.countByUserId(USER_ID)).thenReturn(0L);
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template saved = service.upload(USER_ID, pdfBytes(), "my-resume-v2.pdf", "  ", TemplateDocumentType.RESUME);

            assertThat(saved.name()).isEqualTo("my-resume-v2");
        }

        @Test
        void rejectsAnEmptyFile() {
            assertThatThrownBy(() -> service.upload(USER_ID, new byte[0], "resume.pdf", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        }

        @Test
        void rejectsAFileOverTheSizeLimit() {
            byte[] tooBig = new byte[6 * 1024 * 1024];
            System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, tooBig, 0, 5);

            assertThatThrownBy(() -> service.upload(USER_ID, tooBig, "resume.pdf", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        }

        @Test
        @DisplayName("an unsupported extension is rejected regardless of content")
        void rejectsAnUnsupportedExtension() {
            assertThatThrownBy(() -> service.upload(USER_ID, pdfBytes(), "resume.txt", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        }

        @Test
        @DisplayName("a .pdf extension whose bytes are not really a PDF is rejected — extension alone is never trusted")
        void rejectsAMislabelledPdf() {
            byte[] notActuallyAPdf = "<html>fake</html>".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.upload(USER_ID, notActuallyAPdf, "resume.pdf", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        }

        @Test
        @DisplayName("a .docx extension whose bytes are not really a zip is rejected — extension alone is never trusted")
        void rejectsAMislabelledDocx() {
            byte[] notActuallyAZip = "plain text pretending to be a docx".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.upload(USER_ID, notActuallyAZip, "resume.docx", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        }

        @Test
        @DisplayName("uploading the exact same file twice is rejected as a duplicate, never silently stored twice")
        void rejectsADuplicateUpload() {
            when(templates.existsByUserIdAndSha256(eq(USER_ID), anyString())).thenReturn(true);

            assertThatThrownBy(() -> service.upload(USER_ID, pdfBytes(), "resume.pdf", "Resume", TemplateDocumentType.RESUME))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
            verify(storage, never()).upload(any(), any());
        }
    }

    @Nested
    class Listing {

        @Test
        void listsOnlyTheCallersOwnTemplatesInDescendingCreatedOrder() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(t1));

            List<Template> result = service.list(USER_ID);

            assertThat(result).containsExactly(t1);
        }
    }

    @Nested
    class Ownership {

        @Test
        @DisplayName("requesting another user's template 404s (ApiException.notOwned), never a distinct 403")
        void anotherUsersTemplateIsReportedNotFound() {
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requireOwned(USER_ID, "t-1"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void anOwnedTemplateIsReturned() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(t1));

            assertThat(service.requireOwned(USER_ID, "t-1")).isEqualTo(t1);
        }
    }

    @Nested
    class Rename {

        @Test
        void renamesAnOwnedTemplate() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(t1));
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template renamed = service.rename(USER_ID, "t-1", "New Name");

            assertThat(renamed.name()).isEqualTo("New Name");
        }

        @Test
        void rejectsAnEmptyName() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(t1));

            assertThatThrownBy(() -> service.rename(USER_ID, "t-1", "   "))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        void renamingSomeoneElsesTemplate404s() {
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rename(USER_ID, "t-1", "New Name"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    class DefaultTemplate {

        @Test
        @DisplayName("setting a new default unsets whichever template previously held it")
        void settingANewDefaultUnsetsThePreviousOne() {
            Template previousDefault = pdfTemplate("t-1", USER_ID);
            previousDefault.markDefault(true);
            Template next = pdfTemplate("t-2", USER_ID);

            when(templates.findByIdAndUserId("t-2", USER_ID)).thenReturn(Optional.of(next));
            when(templates.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(previousDefault));
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template result = service.setDefault(USER_ID, "t-2");

            assertThat(result.isDefault()).isTrue();
            assertThat(previousDefault.isDefault()).isFalse();
            verify(templates, times(1)).save(previousDefault);
            verify(templates, times(1)).save(next);
        }

        @Test
        @DisplayName("setting the already-default template default again is a harmless no-op, not a double-unset")
        void settingTheSameTemplateDefaultAgainIsANoOpOnItself() {
            Template current = pdfTemplate("t-1", USER_ID);
            current.markDefault(true);

            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(current));
            when(templates.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(current));
            when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Template result = service.setDefault(USER_ID, "t-1");

            assertThat(result.isDefault()).isTrue();
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesTheStoredObjectAndTheMetadataRow() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(t1));

            service.delete(USER_ID, "t-1");

            verify(storage).delete(t1.objectKey());
            verify(templates).delete(t1);
        }

        @Test
        void deletingSomeoneElsesTemplate404sAndTouchesNoStorage() {
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(USER_ID, "t-1")).isInstanceOf(ApiException.class);

            verify(storage, never()).delete(anyString());
            verify(templates, never()).delete(any(Template.class));
        }
    }

    @Nested
    class Download {

        @Test
        void downloadsTheOriginalBytesOfAnOwnedTemplate() {
            Template t1 = pdfTemplate("t-1", USER_ID);
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.of(t1));
            when(storage.download(t1.objectKey())).thenReturn(pdfBytes());

            byte[] result = service.download(USER_ID, "t-1");

            assertThat(result).isEqualTo(pdfBytes());
        }

        @Test
        void downloadingSomeoneElsesTemplate404sAndNeverTouchesStorage() {
            when(templates.findByIdAndUserId("t-1", USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.download(USER_ID, "t-1")).isInstanceOf(ApiException.class);

            verify(storage, never()).download(anyString());
        }
    }

    // ---- fixtures -----------------------------------------------------------------------

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] docxBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Template pdfTemplate(String id, String userId) {
        Template template = new Template(userId, "Resume", "resume.pdf", TemplateFileType.PDF,
                TemplateDocumentType.RESUME, "object-key-" + id, "bucket", 1024L, "sha-" + id, false);
        setId(template, id);
        return template;
    }

    /** Mongo assigns {@code id} on save via reflection; these tests need a stable id before a
     *  real save ever happens, so it's set the same way. */
    private static void setId(Template template, String id) {
        try {
            java.lang.reflect.Field field = Template.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(template, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
