package ai.careerforge.application.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One contact link in the document header — LinkedIn, GitHub, a portfolio site. Identity data
 * copied from the candidate's own profile, not a claim about their work, so it carries no
 * {@code evidenceId} (see {@link DocumentHeader}).
 *
 * @param label the link's display label, e.g. {@code "LinkedIn"}, {@code "GitHub"}
 * @param url   the link target; {@code http(s)} only
 */
public record HeaderLink(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+") String url) {
}
