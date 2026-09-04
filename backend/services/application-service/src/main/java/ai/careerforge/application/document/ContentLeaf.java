package ai.careerforge.application.document;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One candidate-facing leaf of prose — a resume bullet, a professional-summary paragraph, a
 * cover-letter paragraph. This is the unit ADR-036's rule binds directly: <strong>every
 * candidate-facing leaf requires an {@code evidenceId}</strong>, enforced here by
 * {@code @NotEmpty} rather than left to a convention — a leaf cannot be constructed validly
 * without citing at least one real piece of evidence.
 *
 * <p>Mirrors {@code GroundingValidator.GeneratedStatement}'s shape deliberately
 * ({@code text} + the evidence ids it cites) — the same shape ai-service's grounded content
 * fragments already carry — so a leaf surviving here is one that already passed grounding
 * upstream, in {@code ai-service}, before {@code application-service} ever wove it into an
 * assembled document.
 *
 * @param text        the leaf's rendered text — already grounded, never re-checked for facts
 *                     downstream of this record (rendering is pure presentation, ADR-036)
 * @param evidenceIds  every evidence item this leaf's text is backed by; never empty
 * @param origin       whether this text is a verbatim copy or a grounded rewrite
 */
public record ContentLeaf(
        @NotEmpty @Size(max = 2000) String text,
        @NotEmpty List<@Pattern(regexp = "^(EXP|PROJ|SKILL|CERT|EDU|ACH)-[0-9]{3,4}$") String> evidenceIds,
        @NotNull ContentOrigin origin) {

    public ContentLeaf {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
