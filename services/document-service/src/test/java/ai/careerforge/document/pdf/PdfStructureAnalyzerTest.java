package ai.careerforge.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.TemplateFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * Verifies real PDF analysis — placeholders actually located with correct geometry, and the
 * honest rejection when a PDF has none — not just "didn't throw" (ADR-023, mirrors
 * DocxStructureAnalyzerTest's approach for the DOCX side).
 */
public class PdfStructureAnalyzerTest {

    private final PdfStructureAnalyzer analyzer = new PdfStructureAnalyzer();

    @Test
    void loadRejectsBytesThatAreNotReallyAPdf() {
        byte[] bogus = "not a pdf".getBytes();
        assertThatThrownBy(() -> analyzer.load(bogus))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.FILE_REJECTED);
    }

    @Test
    void analyzeRejectsAPdfWithNoPlaceholders() throws IOException {
        byte[] pdf = onePagePdf("Just some plain text, no tokens here.");
        PDDocument document = analyzer.load(pdf);
        try {
            assertThatThrownBy(() -> analyzer.analyze(document))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.FILE_REJECTED);
        } finally {
            document.close();
        }
    }

    @Test
    void analyzeFindsAPlaceholderWithRealPageAndGeometry() throws IOException {
        byte[] pdf = onePagePdf("Name: {{NAME}}");
        PDDocument document = analyzer.load(pdf);
        try {
            PdfStructureAnalyzer.Analysis analysis = analyzer.analyze(document);

            assertThat(analysis.detectedFields()).hasSize(1);
            DetectedField field = analysis.detectedFields().get(0);
            assertThat(field.token()).isEqualTo("NAME");
            assertThat(field.suggestedField()).isEqualTo("NAME");
            assertThat(field.hasPdfPosition()).isTrue();
            assertThat(field.pdfPage()).isEqualTo(1);
            assertThat(field.pdfFontSizePt()).isGreaterThan(0f);

            assertThat(analysis.structure().sourceType()).isEqualTo(TemplateFormat.PDF);
            assertThat(analysis.structure().pageCount()).isEqualTo(1);
            assertThat(analysis.structure().pageWidthPt()).isEqualTo(PDRectangle.LETTER.getWidth());
            // DOCX-only fields must stay unset for a PDF row — the two formats are never
            // conflated (ADR-023).
            assertThat(analysis.structure().pageWidthTwips()).isNull();
            assertThat(analysis.structure().columnCount()).isZero();
        } finally {
            document.close();
        }
    }

    @Test
    void duplicateTokensAreDedupedKeepingTheFirstOccurrence() throws IOException {
        byte[] pdf = onePagePdf("{{NAME}} appears twice: {{NAME}} again, plus {{EMAIL}}");
        PDDocument document = analyzer.load(pdf);
        try {
            PdfStructureAnalyzer.Analysis analysis = analyzer.analyze(document);
            assertThat(analysis.detectedFields()).extracting(DetectedField::token)
                    .containsExactlyInAnyOrder("NAME", "EMAIL");
        } finally {
            document.close();
        }
    }

    public static byte[] onePagePdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
