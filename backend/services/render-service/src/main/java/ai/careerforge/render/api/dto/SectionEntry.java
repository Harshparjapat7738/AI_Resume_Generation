package ai.careerforge.render.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One item within a resume section — one job, one degree, one project, one certification, one
 * skill — already assembled by application-service. render-service lays these fields out
 * exactly as received; it never decides order, never rewrites a title or a date.
 *
 * @param evidenceId   the one evidence item this entry presents
 * @param title        role title, degree, project name, certification name or skill name
 * @param organisation employer, institution or issuer; nullable
 * @param location     nullable
 * @param startDate    ISO date or year; nullable
 * @param endDate      ISO date, year, or {@code "Present"}; nullable
 * @param bullets      ordered bullets; possibly empty
 */
public record SectionEntry(
        @NotBlank @Pattern(regexp = "^(EXP|PROJ|SKILL|CERT|EDU|ACH)-[0-9]{3,4}$") String evidenceId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String organisation,
        @Size(max = 200) String location,
        @Size(max = 50) String startDate,
        @Size(max = 50) String endDate,
        @Valid List<ContentLeaf> bullets) {

    public SectionEntry {
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
    }
}
