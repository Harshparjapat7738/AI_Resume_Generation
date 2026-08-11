package ai.careerforge.document.docx;

import java.util.ArrayList;
import java.util.List;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Body;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

/**
 * Document-order paragraph/run walking shared by the analyzer and the mail-merge engine — both
 * need the exact same traversal (including table cells, since a template's contact block or a
 * skills grid is commonly laid out in a table) so a placeholder the analyzer finds is
 * guaranteed to be one the merge engine can also find and replace.
 */
final class DocxParagraphs {

    private DocxParagraphs() {
    }

    /** Every paragraph in the document body, in document order, including ones nested inside
     *  table cells. Paragraphs inside headers/footers are out of scope (see
     *  DocxStructureAnalyzer's header/footer note) — those are separate parts docx4j exposes
     *  independently, and this feature's first slice only maps the main body. */
    static List<P> collect(WordprocessingMLPackage pkg) {
        List<P> paragraphs = new ArrayList<>();
        Body body = pkg.getMainDocumentPart().getJaxbElement().getBody();
        collectFrom(body.getContent(), paragraphs);
        return paragraphs;
    }

    private static void collectFrom(List<Object> content, List<P> out) {
        for (Object item : content) {
            Object unwrapped = XmlUtils.unwrap(item);
            if (unwrapped instanceof P p) {
                out.add(p);
            } else if (unwrapped instanceof Tbl tbl) {
                for (Object rowObj : tbl.getContent()) {
                    Object row = XmlUtils.unwrap(rowObj);
                    if (row instanceof Tr tr) {
                        for (Object cellObj : tr.getContent()) {
                            Object cell = XmlUtils.unwrap(cellObj);
                            if (cell instanceof Tc tc) {
                                collectFrom(tc.getContent(), out);
                            }
                        }
                    }
                }
            }
        }
    }

    /** The runs that make up one paragraph's visible text, in order. Runs inside a
     *  {@code w:hyperlink} are intentionally not unwrapped in this first slice — a placeholder
     *  sitting inside link text is rare enough in resume templates that skipping it (rather
     *  than risking incorrect hyperlink-relationship handling on merge) is the safer trade. */
    static List<R> runsOf(P paragraph) {
        List<R> runs = new ArrayList<>();
        for (Object item : paragraph.getContent()) {
            Object unwrapped = XmlUtils.unwrap(item);
            if (unwrapped instanceof R r) {
                runs.add(r);
            }
        }
        return runs;
    }

    /** Concatenated visible text of one run — {@code w:t} elements only; a {@code w:br} or
     *  {@code w:tab} becomes a space so token text never silently glues two words together. */
    static String textOf(R run) {
        StringBuilder sb = new StringBuilder();
        for (Object item : run.getContent()) {
            Object unwrapped = XmlUtils.unwrap(item);
            if (unwrapped instanceof Text t) {
                sb.append(t.getValue());
            } else if (unwrapped instanceof org.docx4j.wml.Br || unwrapped instanceof R.Tab) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** Full text of a paragraph — every run's text concatenated in order. */
    static String textOf(P paragraph) {
        StringBuilder sb = new StringBuilder();
        for (R run : runsOf(paragraph)) {
            sb.append(textOf(run));
        }
        return sb.toString();
    }
}
