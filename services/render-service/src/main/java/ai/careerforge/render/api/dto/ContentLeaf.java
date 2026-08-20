package ai.careerforge.render.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One already-grounded, already-validated leaf of text — a bullet, a summary paragraph, a
 * cover-letter paragraph. By the time this reaches render-service it has already passed
 * {@code GroundingValidator} (ai-service) and {@code DocumentEvidenceValidator}
 * (application-service); render-service never re-checks facts, only lays the text out
 * (ADR-036).
 *
 * <p>{@code evidenceIds} is still required here, not because render-service verifies it, but
 * because "every candidate-facing leaf requires evidenceId" is a shape guarantee the contract
 * itself enforces end to end — a leaf that arrives without one is malformed input, rejected by
 * validation before rendering is ever attempted.
 *
 * @param text        the leaf's rendered text, verbatim
 * @param evidenceIds  every evidence item this leaf is backed by; never empty
 * @param origin      whether the text is a verbatim copy or a grounded rewrite
 */
public record ContentLeaf(
        @NotEmpty @Size(max = 2000) String text,
        @NotEmpty List<@Pattern(regexp = "^(EXP|PROJ|SKILL|CERT|EDU|ACH)-[0-9]{3,4}$") String> evidenceIds,
        @NotNull ContentOrigin origin) {

    public ContentLeaf {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
