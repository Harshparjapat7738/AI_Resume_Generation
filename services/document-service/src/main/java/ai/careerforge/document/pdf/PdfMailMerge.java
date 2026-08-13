package ai.careerforge.document.pdf;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fills a PDF template's {@code {{token}}} placeholders with real content while leaving every
 * other byte of the page — images, lines, logos, other text, backgrounds — untouched. The PDF
 * counterpart of {@code docx.DocxMailMerge} (ADR-023), adapted to PDF's fundamentally different
 * shape: a DOCX placeholder lives in a reflowable paragraph run and Word grows the page to fit;
 * a PDF placeholder occupies a *fixed* rectangle on an already-laid-out page, so this must
 * (1) redact exactly that rectangle — never anything else on the page — and (2) verify the
 * resolved value actually fits it before drawing, condensing or failing rather than silently
 * overlapping/clipping surrounding content (point 6 of the feature spec).
 *
 * <p>Placeholders are re-located fresh against the live {@link PDDocument} being merged (see
 * {@link PdfPlaceholderLocator}), not from coordinates captured at upload time — the same
 * "re-walk the document actually being edited" approach {@code DocxMailMerge} takes with a
 * freshly-loaded package, so a merge is never working from stale geometry.
 */
@Component
public class PdfMailMerge {

    private static final Logger log = LoggerFactory.getLogger(PdfMailMerge.class);
    private static final float LINE_HEIGHT_FACTOR = 1.2f;
    private static final float DEFAULT_MARGIN_PT = 36f; // 0.5in — used only when a page edge is closer than this
    private static final float REDACT_PADDING_PT = 1.5f;
    private static final int MAX_CONDENSE_ATTEMPTS = 6;

    private final PdfContentRedactor redactor = new PdfContentRedactor();

    public byte[] merge(PDDocument document, Map<String, String> resolvedValues) {
        List<PdfPlaceholderLocator.Located> fields = locate(document);

        // Genuinely remove every placeholder's text from each affected page's content stream
        // *before* drawing anything — painting a white rectangle over old text only changes how
        // the page looks, not what its text layer still contains (see PdfContentRedactor's own
        // comment). Done once per page (a page can carry several placeholders; the redactor
        // handles all of them found on that page in one pass).
        try {
            for (int pageNumber : fields.stream().map(PdfPlaceholderLocator.Located::page).distinct().toList()) {
                redactor.redactPlaceholders(document, document.getPage(pageNumber - 1));
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }

        for (PdfPlaceholderLocator.Located field : fields) {
            String value = resolvedValues.getOrDefault(field.token(), "");
            try {
                renderField(document, field, value);
            } catch (IOException ex) {
                throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
            }
        }
        return toBytes(document);
    }

    private List<PdfPlaceholderLocator.Located> locate(PDDocument document) {
        try {
            return PdfPlaceholderLocator.locate(document).located();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }
    }

    /** {@code value == ""} (no mapping resolved anything) simply redacts the placeholder away —
     *  matching {@code DocxMailMerge}'s "no entry -> disappears" behavior — never leaves the
     *  literal {@code {{token}}} text visible in the final document. */
    private void renderField(PDDocument document, PdfPlaceholderLocator.Located field, String value) throws IOException {
        PDPage page = document.getPage(field.page() - 1);
        FitResult fit = fitToBox(document, page, field, value);

        try (PDPageContentStream cs = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            redact(cs, field, fit.lines.size());
            if (!fit.lines.isEmpty()) {
                drawText(cs, field, fit);
            }
        }
    }

    /** Whites out exactly the placeholder's own bounding box, padded slightly to fully occlude
     *  the original glyphs — nothing else on the page is touched. Extends downward (never
     *  sideways) to cover additional wrapped lines only as far as {@link #fitToBox} already
     *  confirmed is free space. */
    private void redact(PDPageContentStream cs, PdfPlaceholderLocator.Located field, int lineCount) throws IOException {
        float extraHeight = Math.max(0, lineCount - 1) * field.fontSizePt() * LINE_HEIGHT_FACTOR;
        cs.setNonStrokingColor(Color.WHITE);
        cs.addRect(
                field.x() - REDACT_PADDING_PT,
                field.y() - field.height() - REDACT_PADDING_PT - extraHeight,
                field.width() + 2 * REDACT_PADDING_PT,
                field.height() + 2 * REDACT_PADDING_PT + extraHeight);
        cs.fill();
    }

    private void drawText(PDPageContentStream cs, PdfPlaceholderLocator.Located field, FitResult fit) throws IOException {
        cs.beginText();
        cs.setFont(fit.font, field.fontSizePt());
        setColor(cs, field.colorHex());
        cs.newLineAtOffset(field.x(), field.y());
        for (int i = 0; i < fit.lines.size(); i++) {
            if (i > 0) {
                cs.newLineAtOffset(0, -field.fontSizePt() * LINE_HEIGHT_FACTOR);
            }
            cs.showText(fit.lines.get(i));
        }
        cs.endText();
    }

    private void setColor(PDPageContentStream cs, String colorHex) throws IOException {
        if (colorHex == null || colorHex.length() != 6) {
            cs.setNonStrokingColor(Color.BLACK);
            return;
        }
        try {
            cs.setNonStrokingColor(Color.decode("#" + colorHex));
        } catch (NumberFormatException ex) {
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    // ---- fit / condense --------------------------------------------------------------------

    private record FitResult(List<String> lines, PDFont font) {
    }

    /** Estimates the available box from the placeholder's own position out to the nearest page
     *  edge (a documented heuristic, not exact layout analysis — this codebase has no general
     *  PDF layout engine, and reconstructing one is out of scope; see ADR-023's "Reason"), wraps
     *  {@code value} to it, and — if it still doesn't fit — condenses the *existing, already-
     *  grounded* text (never invents anything new) by dropping trailing paragraph groups, then
     *  hard-truncating with an ellipsis, re-checking fit after each step. Fails loudly with
     *  {@link ErrorCode#TEMPLATE_CONTENT_OVERFLOW} — naming the field and why — rather than ever
     *  drawing text that overlaps whatever comes after it on the page. */
    private FitResult fitToBox(PDDocument document, PDPage page, PdfPlaceholderLocator.Located field, String value) throws IOException {
        PDFont font = resolvableFont(field, value);
        if (value == null || value.isBlank()) {
            return new FitResult(List.of(), font);
        }

        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        float availableWidth = Math.max(60f, pageWidth - field.x() - Math.min(field.x(), DEFAULT_MARGIN_PT));
        float availableHeight = Math.max(field.fontSizePt() * LINE_HEIGHT_FACTOR, field.y() - DEFAULT_MARGIN_PT);

        String candidate = value;
        for (int attempt = 0; attempt <= MAX_CONDENSE_ATTEMPTS; attempt++) {
            List<String> wrapped = wrap(font, field.fontSizePt(), candidate, availableWidth);
            float neededHeight = wrapped.size() * field.fontSizePt() * LINE_HEIGHT_FACTOR;
            if (neededHeight <= availableHeight) {
                return new FitResult(wrapped, font);
            }
            String condensed = condense(candidate, attempt);
            if (condensed.equals(candidate)) {
                break; // nothing more can be safely removed
            }
            candidate = condensed;
        }

        throw new ApiException(ErrorCode.TEMPLATE_CONTENT_OVERFLOW,
                "The content for {{" + field.token() + "}} doesn't fit this template's layout on page "
                        + field.page() + ", even after condensing it.");
    }

    /** Removes the least essential real text first — the last blank-line-separated paragraph
     *  (a whole Experience/Education entry, for a multi-entry field), then, once only one
     *  paragraph is left, a hard character truncation with an ellipsis. Every step only ever
     *  shortens text that was already there. */
    private String condense(String value, int attempt) {
        String[] paragraphs = value.split("\n\n");
        if (paragraphs.length > 1) {
            return String.join("\n\n", java.util.Arrays.copyOf(paragraphs, paragraphs.length - 1));
        }
        int targetLength = Math.max(20, (int) (value.length() * 0.75));
        if (targetLength >= value.length()) {
            return value;
        }
        return value.substring(0, targetLength).stripTrailing() + "…";
    }

    private List<String> wrap(PDFont font, float fontSizePt, String value, float availableWidth) throws IOException {
        List<String> out = new ArrayList<>();
        for (String paragraphLine : value.split("\n", -1)) {
            if (paragraphLine.isEmpty()) {
                out.add("");
                continue;
            }
            out.addAll(wrapLine(font, fontSizePt, paragraphLine, availableWidth));
        }
        return out;
    }

    private List<String> wrapLine(PDFont font, float fontSizePt, String line, float availableWidth) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : line.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(font, fontSizePt, candidate) <= availableWidth || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                out.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        out.add(current.toString());
        return out;
    }

    private float widthOf(PDFont font, float fontSizePt, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSizePt;
    }

    /** The placeholder's own embedded font, reused as-is when it can actually encode the
     *  replacement text (real profile content can contain characters a subsetted embedded font
     *  never included). Falls back to a standard Helvetica when it can't, rather than let
     *  {@code showText} throw and the whole merge fail over a single unusual character. */
    private PDFont resolvableFont(PdfPlaceholderLocator.Located field, String value) {
        PDFont original = field.font();
        if (original != null && value != null && canEncode(original, value)) {
            return original;
        }
        if (original != null) {
            log.info("PDF template field {{{}}}: embedded font cannot encode the resolved text, falling back to Helvetica",
                    field.token());
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private boolean canEncode(PDFont font, String value) {
        try {
            font.encode(value);
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] toBytes(PDDocument document) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.warn("PDF serialization failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }
    }
}
