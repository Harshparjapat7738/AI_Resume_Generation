package ai.careerforge.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Round-trip verification of the real merged PDF's own extractable text — not just "merge
 * didn't throw" — mirroring how DocxMailMergeTest (and the built-in PdfRendererTest) verify
 * real output. Visual/pixel-level fidelity isn't asserted here (this environment has no
 * PDF-to-image rendering to compare against); every assertion instead checks what the merged
 * document's own text layer actually contains, which is exactly what a downstream ATS parser or
 * copy-paste would see.
 */
class PdfMailMergeTest {

    private final PdfStructureAnalyzer analyzer = new PdfStructureAnalyzer();
    private final PdfMailMerge merge = new PdfMailMerge();

    @Test
    void replacesThePlaceholderWithTheResolvedValueAndLeavesOtherTextAlone() throws IOException {
        byte[] pdf = PdfStructureAnalyzerTest.onePagePdf("Hello {{NAME}}, welcome.");
        byte[] merged = mergeFreshLoad(pdf, Map.of("NAME", "Ada Lovelace"));

        // Checked directly against the raw content stream, not just extracted text: this
        // environment's PDFBox falls back from Helvetica to LiberationSans for text
        // *extraction* (no real Helvetica font package is installed here — see the "Using
        // fallback font" log line), and LiberationSans's slightly different glyph metrics can
        // make PDFTextStripper's own word-gap heuristic misjudge a spacing boundary inside a
        // perfectly well-formed `(Ada Lovelace) Tj` operator. Asserting on the actual bytes
        // written is what proves the merge itself is correct, independent of that unrelated,
        // environment-specific extraction quirk.
        assertThat(rawContentStream(merged)).contains("(Ada Lovelace) Tj");
        assertThat(rawContentStream(merged)).doesNotContain("{{NAME}}");

        String text = extractText(merged);
        assertThat(text).contains("Hello");
        assertThat(text).contains("welcome");
        assertThat(text).doesNotContain("{{NAME}}");
    }

    @Test
    void anUnresolvedPlaceholderDisappearsRatherThanStayingLiteral() throws IOException {
        byte[] pdf = PdfStructureAnalyzerTest.onePagePdf("Value: {{MISSING}}.");
        byte[] merged = mergeFreshLoad(pdf, Map.of());

        String text = extractText(merged);
        assertThat(text).doesNotContain("{{MISSING}}");
        assertThat(text).doesNotContain("MISSING");
    }

    @Test
    void aMultiLineValueIsDrawnAsMultipleLinesAndAllOfItIsExtractable() throws IOException {
        byte[] pdf = PdfStructureAnalyzerTest.onePagePdf("{{EXPERIENCE}}");
        byte[] merged = mergeFreshLoad(pdf, Map.of("EXPERIENCE", "Engineer at Acme\nBuilt real systems"));

        String text = extractText(merged);
        assertThat(text).contains("Engineer at Acme");
        assertThat(text).contains("Built real systems");
    }

    @Test
    void contentThatCannotPossiblyFitFailsCleanlyRatherThanOverlapping() throws IOException {
        // A placeholder pinned 4pt above the bottom margin has essentially no vertical room at
        // all — condensation (capped at a handful of attempts, so real grounded content is
        // never chewed down to nothing) cannot shrink even a short value enough to fit that.
        byte[] pdf = placeholderNearBottomMargin("{{SUMMARY}}");
        String tooTall = "One line\nTwo lines\nThree lines\nFour lines\nFive lines";

        assertThatThrownBy(() -> mergeFreshLoad(pdf, Map.of("SUMMARY", tooTall)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.TEMPLATE_CONTENT_OVERFLOW);
    }

    /** Loads a fresh {@link PDDocument} for the merge (mirroring how
     *  {@code CustomTemplateAssetService.generate} always re-loads rather than reusing the
     *  document {@code analyzer.load} returned during upload-time analysis) and closes it after
     *  extracting the merged bytes — the same load-merge-close lifecycle production code uses. */
    private byte[] mergeFreshLoad(byte[] original, Map<String, String> resolved) throws IOException {
        PDDocument document = analyzer.load(original);
        try {
            return merge.merge(document, resolved);
        } finally {
            document.close();
        }
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String rawContentStream(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new String(document.getPage(0).getContents().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    /** A one-page PDF with {@code text} placed 4pt above the bottom margin — leaving
     *  essentially no vertical room below it, so any replacement value of even one line
     *  overflows regardless of how aggressively it's condensed. */
    private static byte[] placeholderNearBottomMargin(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 40);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
