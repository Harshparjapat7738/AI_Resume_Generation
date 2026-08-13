package ai.careerforge.document.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Finds every {@code {{token}}} placeholder in a live {@link PDDocument}, with the exact page,
 * bounding box, font and color it was set in — the PDF-native equivalent of what
 * {@code docx.DocxStructureAnalyzer}/{@code DocxMailMerge} read off a DOCX's runs (ADR-023).
 *
 * <p>Unlike DOCX, a PDF has no paragraph/run model to walk — only a content stream of
 * text-showing operators. This subclasses {@link PDFTextStripper} (the same parser
 * {@code pdf.PdfStructureAnalyzer} and {@code pdf.PdfMailMerge} both use) and tracks, per page,
 * the character offset of each string PDFBox reports next to the {@link TextPosition}s that
 * produced it — the same "concatenate then regex, map the match back to its source" technique
 * {@code DocxMailMerge} already uses for a run-split placeholder, adapted to PDF's own unit
 * (glyph position, not run boundaries).
 *
 * <p>Reused by both analysis (upload time) and merge (generation time) — see each class's own
 * comment for why merge re-locates fresh against the document actually being edited rather than
 * reusing coordinates captured at upload time.
 */
class PdfPlaceholderLocator extends PDFTextStripper {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]{1,60})\\s*\\}\\}");

    /** One resolved placeholder occurrence, in reading order. {@code page} is 1-based, matching
     *  {@code PDDocument.getPage(page - 1)}. Coordinates are PDF user-space points (origin
     *  bottom-left) — the same space {@code PDPageContentStream} draws in, so
     *  {@code pdf.PdfMailMerge} can redact/redraw directly from these values with no further
     *  conversion. {@code font} is the live {@link PDFont} resource from the page actually being
     *  read — reusable as-is for redrawing replacement text in the identical typeface, the same
     *  "clone the original's own formatting" approach {@code DocxMailMerge} takes with
     *  {@code w:rPr}. */
    record Located(int page, String token, String context, float x, float y, float width, float height,
                    float fontSizePt, PDFont font, String colorHex) {
    }

    private static final class Span {
        final int start;
        final int end;
        final List<TextPosition> positions;
        /** Captured at {@link #writeString} time, not lazily — the graphics state (and so the
         *  active fill color) has moved on by the time a later placeholder match is resolved in
         *  {@link #endPage}, so it must be read the instant this span's text is actually drawn,
         *  never re-queried afterward. */
        final String colorHex;

        Span(int start, int end, List<TextPosition> positions, String colorHex) {
            this.start = start;
            this.end = end;
            this.positions = positions;
            this.colorHex = colorHex;
        }
    }

    private final List<Located> located = new ArrayList<>();
    private final StringBuilder pageText = new StringBuilder();
    private final List<Span> pageSpans = new ArrayList<>();
    private int currentPage;

    // Document-wide facts (every text position, not only ones inside a placeholder) — the PDF
    // equivalent of DocxStructureAnalyzer walking every run's rPr, used by PdfStructureAnalyzer
    // to report real font/size/color diversity for the whole template, not just its
    // placeholders.
    private final java.util.LinkedHashSet<String> fontsUsed = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Double> fontSizesPt = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<String> colorsUsedHex = new java.util.LinkedHashSet<>();

    PdfPlaceholderLocator() throws IOException {
        setSortByPosition(true);
    }

    /** Placeholders found, in reading order, plus whatever document-wide facts were gathered
     *  along the way (see {@link #fontsUsedDocumentWide()} etc.) — one pass over the document
     *  serves both {@code pdf.PdfStructureAnalyzer}'s placeholder list and its structural
     *  summary. */
    static PdfPlaceholderLocator locate(PDDocument document) throws IOException {
        PdfPlaceholderLocator locator = new PdfPlaceholderLocator();
        locator.getText(document); // drives startPage/writeString/endPage for every page
        return locator;
    }

    List<Located> located() {
        return located;
    }

    List<String> fontsUsedDocumentWide() {
        return List.copyOf(fontsUsed);
    }

    List<Double> fontSizesPtDocumentWide() {
        return List.copyOf(fontSizesPt);
    }

    List<String> colorsUsedHexDocumentWide() {
        return List.copyOf(colorsUsedHex);
    }

    private float currentPageHeight;

    @Override
    protected void startPage(PDPage page) throws IOException {
        currentPage++;
        currentPageHeight = page.getMediaBox().getHeight();
        pageText.setLength(0);
        pageSpans.clear();
        super.startPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        String color = nonStrokingColorHex(); // read now — this is the only correct moment
        int start = pageText.length();
        pageText.append(text);
        pageSpans.add(new Span(start, pageText.length(), textPositions, color));

        for (TextPosition position : textPositions) {
            if (position.getFont() != null && position.getFont().getName() != null) {
                fontsUsed.add(position.getFont().getName());
            }
            fontSizesPt.add((double) Math.round(position.getFontSizeInPt() * 10) / 10.0);
        }
        if (color != null) {
            colorsUsedHex.add(color);
        }
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        pageText.append(getWordSeparator());
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        pageText.append(getLineSeparator());
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        Matcher matcher = PLACEHOLDER.matcher(pageText);
        while (matcher.find()) {
            List<Span> covering = spansCovering(matcher.start(), matcher.end());
            if (covering.isEmpty()) {
                continue; // matched entirely inside separator whitespace — not a real placeholder
            }
            located.add(build(matcher.group(1), excerpt(matcher.start(), matcher.end()), covering));
        }
        super.endPage(page);
    }

    private List<Span> spansCovering(int matchStart, int matchEnd) {
        List<Span> result = new ArrayList<>();
        for (Span span : pageSpans) {
            if (span.start < matchEnd && span.end > matchStart) {
                result.add(span);
            }
        }
        return result;
    }

    /** {@code TextPosition.getX()}/{@code getY()} are in PDFBox's own top-down text-extraction
     *  space (Y measured downward from the *top* of the page — verified empirically: a glyph
     *  drawn at native content-stream {@code y=700} on a 792pt-tall page reports
     *  {@code getY()=92}, exactly {@code pageHeight - 700}), not PDF's native bottom-up content-
     *  stream space that {@code PDPageContentStream}/{@code cs.newLineAtOffset(x, y)} draw in.
     *  {@code Located.y}/{@code height} are converted back to native space here — the one place
     *  that conversion needs to happen — so every downstream consumer ({@code PdfMailMerge}'s
     *  redact-and-redraw) can use these coordinates directly with no further adjustment. */
    private Located build(String token, String context, List<Span> spans) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minTopDownY = Float.MAX_VALUE;
        float maxTopDownY = -Float.MAX_VALUE;
        TextPosition first = null;
        for (Span span : spans) {
            for (TextPosition p : span.positions) {
                if (first == null) first = p;
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX() + p.getWidth());
                minTopDownY = Math.min(minTopDownY, p.getY() - p.getHeight());
                maxTopDownY = Math.max(maxTopDownY, p.getY());
            }
        }
        float nativeBaselineY = currentPageHeight - maxTopDownY;
        float height = maxTopDownY - minTopDownY;
        return new Located(currentPage, token, context, minX, nativeBaselineY, maxX - minX, height,
                first.getFontSizeInPt(), first.getFont(), spans.get(0).colorHex);
    }

    /** The non-stroking (fill) color active in the graphics state at the current point in the
     *  content stream — {@code null} for an exotic/unsupported colorspace, since color is
     *  cosmetic only and must never block analysis or a merge. */
    private String nonStrokingColorHex() {
        try {
            PDColor color = getGraphicsState().getNonStrokingColor();
            float[] rgb = color.getColorSpace().toRGB(color.getComponents());
            return String.format(Locale.ROOT, "%02X%02X%02X",
                    Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private String excerpt(int start, int end) {
        int from = Math.max(0, start - 20);
        int to = Math.min(pageText.length(), end + 20);
        String prefix = from > 0 ? "…" : "";
        String suffix = to < pageText.length() ? "…" : "";
        return (prefix + pageText.substring(from, to) + suffix).trim();
    }
}
