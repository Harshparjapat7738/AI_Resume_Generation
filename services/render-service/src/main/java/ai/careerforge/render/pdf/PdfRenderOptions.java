package ai.careerforge.render.pdf;

import ai.careerforge.render.api.dto.PageSize;
import java.util.List;

/**
 * Physical layout options for one PDF conversion — page size, margins, which local fonts to
 * embed, and the base URI relative image/resource references resolve against. Deliberately
 * separate from the HTML content itself: {@link OpenHtmlToPdfRenderer} treats the HTML it is
 * given as opaque, already-final markup and applies these options only at the conversion stage.
 *
 * @param pageSize  reuses {@code ai.careerforge.render.api.dto.PageSize} — the same enum
 *                  {@code RenderHints} already carries, so a caller maps one directly to the
 *                  other rather than this package inventing a second page-size vocabulary
 * @param margins   page margins in points
 * @param fonts     local fonts to embed; possibly empty — see {@link FontResource}
 * @param baseUri   resolves relative {@code <img>}/resource URIs in the HTML; empty string
 *                  when there is none (never {@code null} — passed straight to OpenHTMLToPDF)
 */
public record PdfRenderOptions(PageSize pageSize, Margins margins, List<FontResource> fonts, String baseUri) {

    public PdfRenderOptions {
        fonts = fonts == null ? List.of() : List.copyOf(fonts);
        baseUri = baseUri == null ? "" : baseUri;
    }

    /** A4, standard margins, no embedded fonts, no base URI — the common case for the one
     *  built-in template this service ships today. */
    public static PdfRenderOptions defaults() {
        return new PdfRenderOptions(PageSize.A4, Margins.standard(), List.of(), "");
    }
}
