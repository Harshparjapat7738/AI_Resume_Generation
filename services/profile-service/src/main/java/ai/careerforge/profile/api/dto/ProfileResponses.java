package ai.careerforge.profile.api.dto;

import java.util.List;

public final class ProfileResponses {

    private ProfileResponses() {
    }

    public record PersonalInformationResponse(
            String fullName, String headline, String email, String phone, List<String> links) {
    }

    public record ExperienceResponse(
            String evidenceId, String company, String title, String employmentType,
            String start, String end, boolean current, String location,
            List<String> bullets, List<String> technologies, List<String> metrics) {
    }

    public record ProfileResponse(
            PersonalInformationResponse personalInformation, List<ExperienceResponse> experiences) {
    }

    /**
     * Shape must match {@code ai.careerforge.ai.api.dto.EvidenceItem} field-for-field — the
     * two services don't share a module (ADR-006), only a JSON contract. resume-service
     * fetches this list and forwards it verbatim into ai-service's evidence-selection and
     * resume-content requests.
     */
    public record EvidenceItemResponse(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate) {
    }
}
