package ai.careerforge.render.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One contact link in the document header — LinkedIn, GitHub, a portfolio site. */
public record HeaderLink(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+") String url) {
}
