package ai.careerforge.render.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The candidate's identity block. No {@code evidenceId} — a name/email/phone is identity data,
 * not a candidate claim to ground. <strong>No photo field, deliberately</strong> (ADR-036): a
 * candidate photograph is an ATS-parsing risk and a hiring-bias surface this platform does not
 * introduce, on the resume or the cover letter.
 */
public record DocumentHeader(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 40) String phone,
        @Size(max = 200) String location,
        @Valid List<HeaderLink> links) {

    public DocumentHeader {
        links = links == null ? List.of() : List.copyOf(links);
    }
}
