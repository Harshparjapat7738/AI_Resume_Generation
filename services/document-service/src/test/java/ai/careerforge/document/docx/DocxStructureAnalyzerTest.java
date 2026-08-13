package ai.careerforge.document.docx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.TemplateFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

/**
 * Verifies real DOCX analysis — placeholders actually found in the OOXML, real structural
 * facts read off it — not just "didn't throw". Unlike the PDF analyzer (ADR-023), a DOCX with
 * zero placeholders is a perfectly normal, accepted upload — DOCX mail-merge still works fine
 * as a plain preserve-my-layout template with nothing to fill in, whereas a PDF genuinely
 * cannot represent that case reliably (see PdfStructureAnalyzerTest).
 */
public class DocxStructureAnalyzerTest {

    private final DocxStructureAnalyzer analyzer = new DocxStructureAnalyzer();

    @Test
    void loadRejectsBytesThatAreNotReallyADocx() {
        byte[] bogus = "not a docx".getBytes();
        assertThatThrownBy(() -> analyzer.load(bogus))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void findsAPlaceholderAndSuggestsItsProfileFieldMapping() throws Docx4JException, IOException {
        byte[] docx = onePagePackage("Name: {{NAME}}");
        WordprocessingMLPackage pkg = analyzer.load(docx);

        DocxStructureAnalyzer.Analysis analysis = analyzer.analyze(pkg);

        assertThat(analysis.detectedFields()).hasSize(1);
        DetectedField field = analysis.detectedFields().get(0);
        assertThat(field.token()).isEqualTo("NAME");
        assertThat(field.suggestedField()).isEqualTo("NAME");
        assertThat(field.hasPdfPosition()).isFalse(); // DOCX placeholders have no fixed position

        assertThat(analysis.structure().sourceType()).isEqualTo(TemplateFormat.DOCX);
        assertThat(analysis.structure().paragraphCount()).isGreaterThanOrEqualTo(1);
        // PDF-only fields must stay unset for a DOCX row.
        assertThat(analysis.structure().pageCount()).isNull();
    }

    @Test
    void aDocxWithNoPlaceholdersIsStillAcceptedUnlikePdf() throws Docx4JException, IOException {
        byte[] docx = onePagePackage("Just plain text, nothing to fill in.");
        WordprocessingMLPackage pkg = analyzer.load(docx);

        DocxStructureAnalyzer.Analysis analysis = analyzer.analyze(pkg);

        assertThat(analysis.detectedFields()).isEmpty();
        assertThat(analysis.structure()).isNotNull();
    }

    public static byte[] onePagePackage(String paragraphText) throws Docx4JException, IOException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText(paragraphText);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }
}
