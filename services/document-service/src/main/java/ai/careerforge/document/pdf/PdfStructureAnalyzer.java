package ai.careerforge.document.pdf;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.TemplateStructure;
import ai.careerforge.document.docx.ProfileFieldCatalog;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * Reads real structural facts straight off an uploaded PDF (page count/dimensions, fonts,
 * sizes, colors) and finds every {@code {{token}}} placeholder in it, with the exact page and
 * geometry {@code pdf.PdfMailMerge} needs to fill it in later — the PDF counterpart of
 * {@code docx.DocxStructureAnalyzer} (ADR-023). A PDF has no OOXML section properties or
 * paragraph/run model, so this reads PDF-native facts (via {@link PdfPlaceholderLocator}, a
 * {@link org.apache.pdfbox.text.PDFTextStripper}) instead — never an approximation of the DOCX
 * shape.
 *
 * <p>Nothing here executes anything in the file: PDFBox only parses PDF objects and content
 * streams into its own object model — the same "load and read" operation a PDF viewer's parser
 * performs — with no script/JavaScript execution path exercised by this class (PDF JavaScript,
 * where present, is never invoked by PDFBox's parser).
 */
@Component
public class PdfStructureAnalyzer {

    public record Analysis(TemplateStructure structure, List<DetectedField> detectedFields) {
    }

    /** Parses {@code bytes} as a PDF. A file that isn't really a PDF — wrong format, corrupted,
     *  encrypted with no accessible password, or something crafted to look like one — fails
     *  here with a safe, generic rejection rather than partially processing it. */
    public PDDocument load(byte[] bytes) {
        try {
            PDDocument document = Loader.loadPDF(bytes);
            if (document.isEncrypted()) {
                document.close();
                throw new ApiException(ErrorCode.FILE_REJECTED,
                        "Password-protected PDFs are not supported. Remove the password and try again.");
            }
            return document;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.FILE_REJECTED,
                    "The file could not be read as a PDF document. Re-save it as PDF and try again.");
        }
    }

    /** Never returns a structure/field list for a PDF with zero detected placeholders — a
     *  template nothing could actually fill in is rejected here rather than accepted and
     *  silently rendered unchanged later (point 7 of the feature spec: reject honestly when a
     *  PDF's structure can't be safely determined). */
    public Analysis analyze(PDDocument document) {
        PdfPlaceholderLocator located;
        try {
            located = PdfPlaceholderLocator.locate(document);
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.FILE_REJECTED,
                    "This PDF's text could not be analyzed. It may be a scanned image rather than real text — "
                            + "only PDFs with real, selectable text can be used as templates.");
        }

        if (located.located().isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REJECTED,
                    "No {{placeholder}} fields (like {{NAME}} or {{SUMMARY}}) were found in this PDF's text. "
                            + "Add placeholders where you want your content to appear, or use a .docx template instead.");
        }

        List<DetectedField> detectedFields = dedupeByToken(located.located());
        TemplateStructure structure = TemplateStructure.forPdf(
                document.getNumberOfPages(),
                document.getNumberOfPages() > 0 ? document.getPage(0).getMediaBox().getWidth() : null,
                document.getNumberOfPages() > 0 ? document.getPage(0).getMediaBox().getHeight() : null,
                located.fontsUsedDocumentWide(),
                located.fontSizesPtDocumentWide(),
                located.colorsUsedHexDocumentWide(),
                List.of());
        return new Analysis(structure, detectedFields);
    }

    private List<DetectedField> dedupeByToken(List<PdfPlaceholderLocator.Located> located) {
        LinkedHashMap<String, DetectedField> byToken = new LinkedHashMap<>();
        for (PdfPlaceholderLocator.Located l : located) {
            byToken.putIfAbsent(l.token(), new DetectedField(
                    l.token(), l.context(), ProfileFieldCatalog.suggest(l.token()),
                    l.page(), l.x(), l.y(), l.width(), l.height(), l.fontSizePt(),
                    l.font() != null ? l.font().getName() : null, l.colorHex()));
        }
        return List.copyOf(byToken.values());
    }
}
