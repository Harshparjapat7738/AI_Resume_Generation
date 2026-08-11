package ai.careerforge.document.docx;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Br;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fills a custom template's {@code {{token}}} placeholders with real content while leaving
 * every other byte of the original document — layout, fonts, colors, spacing, headers/footers,
 * tables, columns, everything not itself a placeholder — completely untouched. This is what
 * "preserve the user's template as closely as technically possible" (point 5 of the feature
 * spec) means in practice for DOCX: never re-render through HTML/PDF (that's the built-in
 * pipeline, and it would produce a generic layout, not the user's own), only ever
 * surgically replace the specific runs that carry a placeholder token, cloning each replaced
 * run's own {@code w:rPr} so the substituted text renders in exactly the font/size/color/bold/
 * italic the placeholder itself was styled with.
 *
 * <p>A placeholder split across multiple runs (Word frequently splits a single visible word
 * into several runs after any edit, e.g. spell-check) is still found and replaced correctly:
 * matching happens against each paragraph's full concatenated text, and the replacement run is
 * built from whichever original run the placeholder's first character fell in — the runs that
 * used to hold the placeholder's later characters are simply omitted since their text is now
 * part of the single replacement run.
 */
@Component
public class DocxMailMerge {

    private static final Logger log = LoggerFactory.getLogger(DocxMailMerge.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]{1,60})\\s*\\}\\}");
    private static final ObjectFactory FACTORY = Context.getWmlObjectFactory();

    /** Replaces every {@code {{token}}} in the document body whose token is a key in
     *  {@code resolvedValues}; a detected token with no entry (or a blank value) is removed —
     *  the placeholder disappears rather than being left literally visible in the final
     *  document. Returns the re-serialized .docx bytes. */
    public byte[] merge(WordprocessingMLPackage pkg, Map<String, String> resolvedValues) {
        for (P paragraph : DocxParagraphs.collect(pkg)) {
            mergeParagraph(paragraph, resolvedValues);
        }
        return toBytes(pkg);
    }

    private void mergeParagraph(P paragraph, Map<String, String> resolvedValues) {
        List<R> runs = DocxParagraphs.runsOf(paragraph);
        if (runs.isEmpty()) return;

        int[] starts = new int[runs.size()];
        int[] ends = new int[runs.size()];
        StringBuilder fullTextBuilder = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            starts[i] = fullTextBuilder.length();
            fullTextBuilder.append(DocxParagraphs.textOf(runs.get(i)));
            ends[i] = fullTextBuilder.length();
        }
        String fullText = fullTextBuilder.toString();

        Matcher matcher = PLACEHOLDER.matcher(fullText);
        if (!matcher.find()) return; // no placeholder in this paragraph — leave it byte-for-byte alone

        List<Object> newContent = new ArrayList<>();
        int pos = 0;
        matcher.reset();
        while (matcher.find()) {
            appendSlice(newContent, runs, starts, ends, pos, matcher.start());
            R templateRun = runAt(runs, starts, ends, matcher.start());
            String value = resolvedValues.getOrDefault(matcher.group(1), "");
            newContent.addAll(buildReplacementRuns(templateRun, value));
            pos = matcher.end();
        }
        appendSlice(newContent, runs, starts, ends, pos, fullText.length());

        paragraph.getContent().clear();
        paragraph.getContent().addAll(newContent);
    }

    /** Rebuilds the original (non-placeholder) text in {@code [from, to)}, run by run, each
     *  new run cloning the {@code w:rPr} of whichever original run that slice of text came
     *  from — so unrelated formatting boundaries inside the same paragraph are preserved
     *  exactly as they were. */
    private void appendSlice(List<Object> out, List<R> runs, int[] starts, int[] ends, int from, int to) {
        if (from >= to) return;
        for (int i = 0; i < runs.size(); i++) {
            int sliceStart = Math.max(from, starts[i]);
            int sliceEnd = Math.min(to, ends[i]);
            if (sliceStart >= sliceEnd) continue;
            String runText = DocxParagraphs.textOf(runs.get(i));
            String piece = runText.substring(sliceStart - starts[i], sliceEnd - starts[i]);
            if (piece.isEmpty()) continue;
            out.add(cloneRunWithText(runs.get(i), piece));
        }
    }

    private R runAt(List<R> runs, int[] starts, int[] ends, int offset) {
        for (int i = 0; i < runs.size(); i++) {
            if (offset >= starts[i] && offset < ends[i]) {
                return runs.get(i);
            }
        }
        return runs.get(runs.size() - 1);
    }

    private R cloneRunWithText(R template, String text) {
        R run = FACTORY.createR();
        run.setRPr(cloneRPr(template));
        run.getContent().add(newText(text));
        return run;
    }

    /** {@code value} may contain newlines (a multi-entry field like Experience/Education
     *  resolves to several lines) — DOCX only breaks a visible line at an explicit
     *  {@code w:br}, so each line becomes its own run/break pair, all cloning the same
     *  placeholder-run formatting. */
    private List<Object> buildReplacementRuns(R template, String value) {
        List<Object> out = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return out; // placeholder simply disappears
        }
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            R run = FACTORY.createR();
            run.setRPr(cloneRPr(template));
            if (!lines[i].isEmpty()) {
                run.getContent().add(newText(lines[i]));
            }
            if (i < lines.length - 1) {
                Br br = FACTORY.createBr();
                run.getContent().add(br);
            }
            out.add(run);
        }
        return out;
    }

    private RPr cloneRPr(R template) {
        if (template.getRPr() == null) return null;
        return org.docx4j.XmlUtils.deepCopy(template.getRPr());
    }

    private Text newText(String value) {
        Text text = FACTORY.createText();
        text.setValue(value);
        text.setSpace("preserve");
        return text;
    }

    private byte[] toBytes(WordprocessingMLPackage pkg) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pkg.save(out);
            return out.toByteArray();
        } catch (Docx4JException | java.io.IOException ex) {
            log.warn("DOCX serialization failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }
    }
}
