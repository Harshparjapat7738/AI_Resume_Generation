package ai.careerforge.document.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Actually removes {{token}} placeholder text from a page's content stream — as opposed to
 * drawing over it, which only changes how the page *looks*, not what its text layer still
 * contains (an ATS parser or a copy-paste would still see the old placeholder underneath a
 * painted-over rectangle). {@link PdfMailMerge} calls this before drawing the resolved value,
 * so the placeholder is genuinely gone, not merely hidden.
 *
 * <p>Rewrites only the specific {@code Tj}/{@code TJ} (show-text) operators whose decoded text
 * overlaps a matched placeholder, splicing out just the matched characters exactly the way
 * {@code DocxMailMerge} splices a matched substring out of a paragraph's runs — a fully-matched
 * piece is dropped, a partially-matched one keeps its non-matched characters, re-encoded in the
 * same font. Every other operator (positioning, color, images, other text, path drawing —
 * everything that makes up the rest of the page) is copied through completely unchanged, in the
 * same order, so nothing else on the page is affected.
 */
final class PdfContentRedactor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]{1,60})\\s*\\}\\}");

    /** One decoded piece of text-showing content: either a whole {@code Tj} string, or one
     *  string element inside a {@code TJ} array. {@code globalStart}/{@code globalEnd} are
     *  offsets into the page-wide concatenated text used for placeholder matching. */
    private record Piece(String text, int globalStart, int globalEnd) {
    }

    void redactPlaceholders(PDDocument document, PDPage page) throws IOException {
        List<Object> tokens = new PDFStreamParser(page).parse();
        List<Piece> pieces = collectPieces(page, tokens);
        if (pieces.isEmpty()) {
            return; // nothing to redact — leave the stream untouched
        }

        String pageText = pieces.stream().map(Piece::text).reduce("", (a, b) -> a + b);
        List<int[]> matches = findMatches(pageText);
        if (matches.isEmpty()) {
            return;
        }

        List<Object> rewritten = rebuild(page, tokens, pieces, matches);
        replaceContents(document, page, rewritten);
    }

    private List<Piece> collectPieces(PDPage page, List<Object> tokens) throws IOException {
        List<Piece> pieces = new ArrayList<>();
        List<Object> pending = new ArrayList<>();
        PDFont currentFont = null;
        int offset = 0;

        for (Object token : tokens) {
            if (!(token instanceof Operator operator)) {
                pending.add(token);
                continue;
            }
            String name = operator.getName();
            if ("Tf".equals(name) && pending.size() >= 2 && pending.get(pending.size() - 2) instanceof COSName fontName) {
                currentFont = resolveFont(page, fontName);
            } else if ("Tj".equals(name) && !pending.isEmpty() && pending.get(pending.size() - 1) instanceof COSString str
                    && currentFont != null) {
                String text = decode(currentFont, str);
                pieces.add(new Piece(text, offset, offset + text.length()));
                offset += text.length();
            } else if ("TJ".equals(name) && !pending.isEmpty() && pending.get(pending.size() - 1) instanceof COSArray array
                    && currentFont != null) {
                for (int i = 0; i < array.size(); i++) {
                    COSBase element = array.get(i);
                    if (element instanceof COSString str) {
                        String text = decode(currentFont, str);
                        pieces.add(new Piece(text, offset, offset + text.length()));
                        offset += text.length();
                    }
                }
            }
            pending.clear();
        }
        return pieces;
    }

    private List<int[]> findMatches(String pageText) {
        List<int[]> matches = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(pageText);
        while (matcher.find()) {
            matches.add(new int[] {matcher.start(), matcher.end()});
        }
        return matches;
    }

    /** Splices any characters of {@code [start, end)} that overlap a match out of the piece's
     *  own text — independently per piece, so a placeholder split across two adjacent
     *  {@code Tj}/{@code TJ} pieces (e.g. a PDF generator emitting {@code "{{"} and
     *  {@code "NAME}}"} separately) is still fully removed even though neither piece alone
     *  contains the whole token. */
    private String keptText(String pieceText, int globalStart, List<int[]> matches) {
        boolean[] remove = new boolean[pieceText.length()];
        for (int[] match : matches) {
            int from = Math.max(match[0], globalStart) - globalStart;
            int to = Math.min(match[1], globalStart + pieceText.length()) - globalStart;
            for (int i = Math.max(0, from); i < Math.min(pieceText.length(), to); i++) {
                remove[i] = true;
            }
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < pieceText.length(); i++) {
            if (!remove[i]) kept.append(pieceText.charAt(i));
        }
        return kept.toString();
    }

    private List<Object> rebuild(PDPage page, List<Object> tokens, List<Piece> pieces, List<int[]> matches) throws IOException {
        List<Object> output = new ArrayList<>();
        List<Object> pending = new ArrayList<>();
        PDFont currentFont = null;
        int pieceIndex = 0;

        for (Object token : tokens) {
            if (!(token instanceof Operator operator)) {
                pending.add(token);
                continue;
            }
            String name = operator.getName();
            if ("Tf".equals(name) && pending.size() >= 2 && pending.get(pending.size() - 2) instanceof COSName fontName) {
                currentFont = resolveFont(page, fontName);
                output.addAll(pending);
                output.add(operator);
            } else if ("Tj".equals(name) && !pending.isEmpty() && pending.get(pending.size() - 1) instanceof COSString str
                    && currentFont != null && pieceIndex < pieces.size()) {
                Piece piece = pieces.get(pieceIndex++);
                String kept = keptText(piece.text(), piece.globalStart(), matches);
                if (kept.equals(piece.text())) {
                    output.addAll(pending);
                    output.add(operator);
                } else if (!kept.isEmpty()) {
                    List<Object> newPending = new ArrayList<>(pending.subList(0, pending.size() - 1));
                    newPending.add(new COSString(currentFont.encode(kept)));
                    output.addAll(newPending);
                    output.add(operator);
                }
                // kept.isEmpty() -> the whole Tj (operand + operator) is dropped
            } else if ("TJ".equals(name) && !pending.isEmpty() && pending.get(pending.size() - 1) instanceof COSArray array
                    && currentFont != null) {
                COSArray newArray = new COSArray();
                for (int i = 0; i < array.size(); i++) {
                    COSBase element = array.get(i);
                    if (element instanceof COSString) {
                        if (pieceIndex >= pieces.size()) continue;
                        Piece piece = pieces.get(pieceIndex++);
                        String kept = keptText(piece.text(), piece.globalStart(), matches);
                        if (!kept.isEmpty()) {
                            newArray.add(new COSString(currentFont.encode(kept)));
                        }
                    } else if (element instanceof COSNumber) {
                        // A kerning adjustment — keep it only if there's already something in
                        // the rebuilt array to adjust; a leading/orphaned one is dropped rather
                        // than left to misposition whatever survives.
                        if (newArray.size() > 0) {
                            newArray.add(element);
                        }
                    }
                }
                if (newArray.size() > 0) {
                    List<Object> newPending = new ArrayList<>(pending.subList(0, pending.size() - 1));
                    newPending.add(newArray);
                    output.addAll(newPending);
                    output.add(operator);
                }
                // an empty rebuilt array -> the whole TJ (operand + operator) is dropped
            } else {
                output.addAll(pending);
                output.add(operator);
            }
            pending.clear();
        }
        return output;
    }

    private void replaceContents(PDDocument document, PDPage page, List<Object> rewrittenTokens) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ContentStreamWriter(bytes).writeTokens(rewrittenTokens);
        try (InputStream in = new ByteArrayInputStream(bytes.toByteArray())) {
            page.setContents(new PDStream(document, in));
        }
    }

    private String decode(PDFont font, COSString string) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(string.getBytes())) {
            while (in.available() > 0) {
                int code = font.readCode(in);
                text.append(font.toUnicode(code));
            }
        }
        return text.toString();
    }

    private PDFont resolveFont(PDPage page, COSName fontName) {
        try {
            return page.getResources() == null ? null : page.getResources().getFont(fontName);
        } catch (IOException ex) {
            return null;
        }
    }
}
