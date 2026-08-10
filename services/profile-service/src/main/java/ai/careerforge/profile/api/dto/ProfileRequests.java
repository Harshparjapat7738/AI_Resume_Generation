package ai.careerforge.profile.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProfileRequests {

    private ProfileRequests() {
    }

    public record PersonalInformationRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 200) String headline,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            List<@Size(max = 300) String> links) {
    }

    public record ExperienceRequest(
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 60) String employmentType,
            @Size(max = 20) String start,
            @Size(max = 20) String end,
            boolean current,
            @Size(max = 200) String location,
            List<@Size(max = 500) String> bullets,
            List<@Size(max = 60) String> technologies,
            List<@Size(max = 200) String> metrics) {
    }
}
