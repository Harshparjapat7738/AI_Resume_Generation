package ai.careerforge.document.docx;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.domain.DetectedField;
import ai.careerforge.document.domain.TemplateStructure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Body;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.SectPr;
import org.springframework.stereotype.Component;

/**
 * Reads real structure/style facts straight off an uploaded DOCX's OOXML — see
 * {@link TemplateStructure} — and finds every {@code {{token}}} placeholder in the body text.
 * Nothing here executes anything in the file: docx4j only ever parses the OOXML/XML parts into
 * its object model, the same "load and read" operation Word/LibreOffice's own parsers perform;
 * there is no macro, script or external-entity execution path exercised by this class.
 */
@Component
public class DocxStructureAnalyzer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]{1,60})\\s*\\}\\}");
    private static final Set<String> HEADER_RELATIONSHIP_TYPES = Set.of(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header");
    private static final Set<String> FOOTER_RELATIONSHIP_TYPES = Set.of(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer");

    public record Analysis(TemplateStructure structure, List<DetectedField> detectedFields) {
    }

    /** Parses {@code bytes} as a WordprocessingML (.docx) package. A file that isn't really a
     *  DOCX — wrong format, corrupted, or something crafted to look like one — fails here with
     *  a safe, generic rejection rather than partially processing it. */
    public WordprocessingMLPackage load(byte[] bytes) {
        try {
            return WordprocessingMLPackage.load(new ByteArrayInputStream(bytes));
        } catch (Docx4JException | RuntimeException ex) {
            throw new ApiException(ErrorCode.FILE_REJECTED,
                    "The file could not be read as a .docx document. Re-save it as Word (.docx) format and try again.");
        }
    }

    public Analysis analyze(WordprocessingMLPackage pkg) {
        List<P> paragraphs = DocxParagraphs.collect(pkg);

        Set<String> fonts = new LinkedHashSet<>();
        Set<Double> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        Set<String> alignments = new LinkedHashSet<>();
        List<String> headings = new ArrayList<>();
        List<DetectedField> detectedFields = new ArrayList<>();
        int tableCount = countTables(pkg);

        for (P paragraph : paragraphs) {
            PPr ppr = paragraph.getPPr();
            if (ppr != null && ppr.getJc() != null && ppr.getJc().getVal() != null) {
                alignments.add(ppr.getJc().getVal().value());
            }
            if (isHeadingStyle(ppr)) {
                String text = DocxParagraphs.textOf(paragraph).trim();
                if (!text.isEmpty()) {
                    headings.add(text);
                }
            }

            for (R run : DocxParagraphs.runsOf(paragraph)) {
                RPr rPr = run.getRPr();
                if (rPr != null) {
                    if (rPr.getRFonts() != null) {
                        String font = rPr.getRFonts().getAscii() != null
                                ? rPr.getRFonts().getAscii() : rPr.getRFonts().getHAnsi();
                        if (font != null && !font.isBlank()) fonts.add(font);
                    }
                    if (rPr.getSz() != null && rPr.getSz().getVal() != null) {
                        sizes.add(rPr.getSz().getVal().doubleValue() / 2.0); // half-points -> pt
                    }
                    if (rPr.getColor() != null && rPr.getColor().getVal() != null
                            && !"auto".equalsIgnoreCase(rPr.getColor().getVal())) {
                        colors.add(rPr.getColor().getVal().toUpperCase(java.util.Locale.ROOT));
                    }
                }
            }

            String paragraphText = DocxParagraphs.textOf(paragraph);
            Matcher matcher = PLACEHOLDER.matcher(paragraphText);
            while (matcher.find()) {
                String token = matcher.group(1);
                String context = excerpt(paragraphText, matcher.start(), matcher.end());
                detectedFields.add(new DetectedField(token, context, ProfileFieldCatalog.suggest(token)));
            }
        }

        TemplateStructure structure = buildStructure(pkg, fonts, sizes, colors, alignments, headings, paragraphs.size(), tableCount);
        return new Analysis(structure, dedupeByToken(detectedFields));
    }

    private TemplateStructure buildStructure(WordprocessingMLPackage pkg, Set<String> fonts, Set<Double> sizes,
                                              Set<String> colors, Set<String> alignments, List<String> headings,
                                              int paragraphCount, int tableCount) {
        Body body = pkg.getMainDocumentPart().getJaxbElement().getBody();
        SectPr sectPr = body.getSectPr();

        Integer pageWidth = null;
        Integer pageHeight = null;
        Integer marginTop = null;
        Integer marginBottom = null;
        Integer marginLeft = null;
        Integer marginRight = null;
        int columnCount = 1;

        if (sectPr != null) {
            if (sectPr.getPgSz() != null) {
                pageWidth = toInt(sectPr.getPgSz().getW());
                pageHeight = toInt(sectPr.getPgSz().getH());
            }
            if (sectPr.getPgMar() != null) {
                marginTop = toInt(sectPr.getPgMar().getTop());
                marginBottom = toInt(sectPr.getPgMar().getBottom());
                marginLeft = toInt(sectPr.getPgMar().getLeft());
                marginRight = toInt(sectPr.getPgMar().getRight());
            }
            if (sectPr.getCols() != null && sectPr.getCols().getNum() != null) {
                columnCount = sectPr.getCols().getNum().intValue();
            }
        }

        boolean hasHeader = hasRelationshipType(pkg, HEADER_RELATIONSHIP_TYPES);
        boolean hasFooter = hasRelationshipType(pkg, FOOTER_RELATIONSHIP_TYPES);

        return TemplateStructure.forDocx(
                pageWidth, pageHeight, marginTop, marginBottom, marginLeft, marginRight, columnCount,
                List.copyOf(fonts), List.copyOf(sizes), List.copyOf(colors), List.copyOf(alignments),
                List.copyOf(headings), paragraphCount, tableCount, hasHeader, hasFooter);
    }

    private boolean isHeadingStyle(PPr ppr) {
        if (ppr == null || ppr.getPStyle() == null || ppr.getPStyle().getVal() == null) return false;
        String style = ppr.getPStyle().getVal().toLowerCase(java.util.Locale.ROOT);
        return style.contains("heading") || style.contains("title");
    }

    private boolean hasRelationshipType(WordprocessingMLPackage pkg, Set<String> types) {
        try {
            for (Relationship rel : pkg.getMainDocumentPart().getRelationshipsPart().getRelationships().getRelationship()) {
                if (types.contains(rel.getType())) {
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            // No relationships part, or an unexpected shape — treat as "no header/footer"
            // rather than failing the whole analysis over a cosmetic detail.
        }
        return false;
    }

    private int countTables(WordprocessingMLPackage pkg) {
        // Top-level tables only (a table nested inside another table's cell is not counted
        // separately) — a reasonable simplification for a structural summary, not something
        // the mail-merge step depends on.
        int count = 0;
        for (Object item : pkg.getMainDocumentPart().getJaxbElement().getBody().getContent()) {
            if (org.docx4j.XmlUtils.unwrap(item) instanceof org.docx4j.wml.Tbl) {
                count++;
            }
        }
        return count;
    }

    private static Integer toInt(BigInteger value) {
        return value == null ? null : value.intValue();
    }

    private static String excerpt(String text, int start, int end) {
        int from = Math.max(0, start - 20);
        int to = Math.min(text.length(), end + 20);
        String prefix = from > 0 ? "…" : "";
        String suffix = to < text.length() ? "…" : "";
        return (prefix + text.substring(from, to) + suffix).trim();
    }

    private static List<DetectedField> dedupeByToken(List<DetectedField> fields) {
        java.util.LinkedHashMap<String, DetectedField> byToken = new java.util.LinkedHashMap<>();
        for (DetectedField field : fields) {
            byToken.putIfAbsent(field.token(), field);
        }
        return List.copyOf(byToken.values());
    }
}
