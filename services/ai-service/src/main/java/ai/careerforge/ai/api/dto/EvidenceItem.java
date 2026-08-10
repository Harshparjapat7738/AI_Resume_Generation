package ai.careerforge.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * One verified fact about the candidate, as supplied by profile-service.
 *
 * <p>The {@code evidenceId} is immutable and is the anchor for the whole anti-fabrication
 * mechanism: the model may only cite these ids, and every generated sentence is checked
 * back against the text of the items it cites.
 *
 * @param evidenceId   stable id, e.g. {@code EXP-004}
 * @param type         EXPERIENCE · PROJECT · SKILL · CERTIFICATION · EDUCATION · ACHIEVEMENT
 * @param title        role title, project name, skill name or certification name
 * @param organisation employer, client, issuer or institution
 * @param description  free text the candidate wrote
 * @param technologies named tools and languages attached to this item
 * @param metrics      quantified outcomes the candidate stated — the ONLY numbers that may
 *                     appear in generated content for this item
 * @param startDate    ISO date or year
 * @param endDate      ISO date, year, or "Present"
 */
public record EvidenceItem(
        @NotBlank @Pattern(regexp = "^(EXP|PROJ|SKILL|CERT|EDU|ACH)-[0-9]{3,4}$")
        String evidenceId,
        @NotBlank String type,
        String title,
        String organisation,
        String description,
        List<String> technologies,
        List<String> metrics,
        String startDate,
        String endDate) {

    public EvidenceItem {
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    /** Everything this item asserts, as one searchable string. */
    public String searchableText() {
        return String.join(" ",
                nullToEmpty(title), nullToEmpty(organisation), nullToEmpty(description),
                String.join(" ", technologies), String.join(" ", metrics),
                nullToEmpty(startDate), nullToEmpty(endDate));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
