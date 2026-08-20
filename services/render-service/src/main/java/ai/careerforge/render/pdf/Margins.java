package ai.careerforge.render.pdf;

/** Page margins in PDF points (1/72 inch), applied via the {@code @page} CSS rule
 *  {@link OpenHtmlToPdfRenderer} injects — never baked into a Thymeleaf template, keeping page
 *  geometry a PDF-stage concern (ADR-036: "renderHints separate from content"). */
public record Margins(float topPt, float rightPt, float bottomPt, float leftPt) {

    /** Half an inch (36pt) on every side — a conventional resume margin. */
    public static Margins standard() {
        return new Margins(36f, 36f, 36f, 36f);
    }
}
