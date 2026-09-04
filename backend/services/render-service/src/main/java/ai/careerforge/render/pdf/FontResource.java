package ai.careerforge.render.pdf;

import jakarta.validation.constraints.NotBlank;

/**
 * One local font file to embed under a CSS {@code font-family} name (ADR-036: "only allowlisted
 * fonts embedded" — the allowlist itself is {@code ai.careerforge.render.api.dto.FontFamily};
 * this record is how one of its values gets an actual font program behind it).
 *
 * <p>No font binaries are bundled with render-service yet — the commercial fonts
 * {@code FontFamily} names (Times New Roman, Arial, Calibri, Georgia) are not freely
 * redistributable, and shipping them would be a licensing problem, not an engineering one.
 * {@link OpenHtmlToPdfRenderer} registers whatever {@link FontResource}s it is given and
 * degrades gracefully — never fails a render — when a classpath location doesn't resolve to a
 * real file; the CSS {@code font-family} stack then falls back past it, ultimately to a PDF
 * base-14 standard font every compliant reader already has. Populating this list with real,
 * licensed font files is a deployment/asset concern for a later step, not this one.
 *
 * @param familyName        the CSS {@code font-family} value this font answers to
 * @param classpathLocation e.g. {@code classpath:fonts/liberation-sans.ttf}
 */
public record FontResource(@NotBlank String familyName, @NotBlank String classpathLocation) {
}
