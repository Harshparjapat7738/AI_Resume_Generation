package ai.careerforge.application.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The candidate's identity block — the one part of a resume/cover letter that is deliberately
 * <em>not</em> evidence. A name, an email address, a phone number are the candidate's own
 * account-level identity, verified the same way {@code EmailGenerationService} already treats
 * the candidate's stated name: copied directly from {@code profile-service}'s personal
 * information, never generated, and never requiring an {@code evidenceId} the way a resume
 * bullet does — there is no "evidence" for someone's own name.
 *
 * <p><strong>No photo field, deliberately.</strong> This is a hard platform rule (ADR-036), not
 * an oversight: a candidate photograph is an ATS-parsing risk and an unrelated hiring-bias
 * surface CareerForge does not introduce.
 *
 * @param fullName the candidate's own stated name
 * @param email    contact email; format-checked only, never evidence-backed
 * @param phone    contact phone, free text (international formats vary too widely to pattern);
 *                 nullable
 * @param location city/region the candidate stated; nullable
 * @param links    LinkedIn/GitHub/portfolio links; possibly empty
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
