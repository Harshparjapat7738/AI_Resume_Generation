package ai.careerforge.application.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One item within a {@link ResumeSection} — one job, one degree, one project, one certification,
 * one skill. Represents exactly one profile evidence item, so its structural facts
 * ({@code title}, {@code organisation}, the dates) are copied straight from that evidence,
 * verbatim, by {@code application-service}'s deterministic assembly — never generated or
 * rephrased by ai-service (ADR-036): a job title or employer name is exactly the kind of fact
 * this platform never lets a model touch.
 *
 * <p>{@code evidenceId} is this entry's own anchor and satisfies the "every candidate-facing
 * leaf requires evidenceId" rule for the structural fields; {@code bullets} carry their own,
 * separate {@code evidenceIds} per leaf (a bullet may cite more than the entry's own item — a
 * bullet in an EXPERIENCE entry can also cite a SKILL item it demonstrates).
 *
 * @param evidenceId   the one evidence item this entry presents
 * @param title        role title, degree, project name, certification name or skill name
 * @param organisation employer, institution or issuer; nullable (a bare skill entry has none)
 * @param location     nullable
 * @param startDate    ISO date or year; nullable
 * @param endDate      ISO date, year, or {@code "Present"}; nullable
 * @param bullets      ordered achievement/description bullets; possibly empty (a one-line
 *                     entry, e.g. a skill or certification with nothing further to elaborate)
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
