package ai.careerforge.profile.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    public record EducationRequest(
            @NotBlank @Size(max = 200) String institution,
            @NotBlank @Size(max = 200) String degree,
            @Size(max = 200) String field,
            @Size(max = 20) String start,
            @Size(max = 20) String end,
            @Size(max = 40) String grade,
            @Size(max = 1000) String description) {
    }

    public record SkillRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 60) String category,
            @Size(max = 40) String proficiency,
            @Min(0) @Max(70) Integer yearsOfExperience) {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Size(max = 100) String role,
            List<@Size(max = 60) String> technologies,
            List<@Size(max = 200) String> metrics,
            @Size(max = 300) String githubUrl,
            @Size(max = 300) String liveUrl,
            @Size(max = 20) String start,
            @Size(max = 20) String end) {
    }

    public record CertificationRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String issuer,
            @Size(max = 20) String issuedOn,
            @Size(max = 20) String expiresOn,
            @Size(max = 100) String credentialId,
            @Size(max = 300) String credentialUrl) {
    }

    public record AchievementRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @Size(max = 20) String date) {
    }
}
