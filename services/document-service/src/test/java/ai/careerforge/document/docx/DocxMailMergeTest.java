package ai.careerforge.document.docx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Map;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;

/**
 * Round-trip verification of the real merged DOCX's own text — not just "merge didn't throw".
 */
class DocxMailMergeTest {

    private final DocxMailMerge merge = new DocxMailMerge();

    @Test
    void replacesThePlaceholderAndLeavesSurroundingTextAlone() throws Docx4JException, java.io.IOException {
        WordprocessingMLPackage pkg = load(DocxStructureAnalyzerTest.onePagePackage("Hello {{NAME}}, welcome."));

        byte[] merged = merge.merge(pkg, Map.of("NAME", "Ada Lovelace"));
        String text = textOf(merged);

        assertThat(text).contains("Ada Lovelace");
        assertThat(text).contains("Hello");
        assertThat(text).contains("welcome");
        assertThat(text).doesNotContain("{{NAME}}");
    }

    @Test
    void anUnresolvedPlaceholderDisappearsRatherThanStayingLiteral() throws Docx4JException, java.io.IOException {
        WordprocessingMLPackage pkg = load(DocxStructureAnalyzerTest.onePagePackage("Value: {{MISSING}}."));

        byte[] merged = merge.merge(pkg, Map.of());
        String text = textOf(merged);

        assertThat(text).doesNotContain("{{MISSING}}");
        assertThat(text).doesNotContain("MISSING");
    }

    @Test
    void aMultiLineValueBecomesMultipleLinesAllExtractable() throws Docx4JException, java.io.IOException {
        WordprocessingMLPackage pkg = load(DocxStructureAnalyzerTest.onePagePackage("{{EXPERIENCE}}"));

        byte[] merged = merge.merge(pkg, Map.of("EXPERIENCE", "Engineer at Acme\nBuilt real systems"));
        String text = textOf(merged);

        assertThat(text).contains("Engineer at Acme");
        assertThat(text).contains("Built real systems");
    }

    private WordprocessingMLPackage load(byte[] bytes) throws Docx4JException {
        return WordprocessingMLPackage.load(new ByteArrayInputStream(bytes));
    }

    private String textOf(byte[] docxBytes) throws Docx4JException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
        StringBuilder sb = new StringBuilder();
        for (Object item : pkg.getMainDocumentPart().getContent()) {
            Object unwrapped = org.docx4j.XmlUtils.unwrap(item);
            if (unwrapped instanceof P paragraph) {
                sb.append(DocxParagraphs.textOf(paragraph)).append('\n');
            }
        }
        return sb.toString();
    }
}
