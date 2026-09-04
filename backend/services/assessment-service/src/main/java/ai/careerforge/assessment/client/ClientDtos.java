package ai.careerforge.assessment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Local mirrors of the DTOs owned by resume-service, jd-service and profile-service. Each
 * service defines its own client-side copy of the contracts it consumes — there is no
 * shared DTO module (ADR-006), only an agreed JSON shape.
 *
 * <p>All mirrors are {@code @JsonIgnoreProperties(ignoreUnknown = true)}: the owning
 * service is free to add fields (e.g. profile-service adding education/skills/projects)
 * without this client needing to change in lockstep or breaking deserialization the moment
 * it doesn't.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ---- resume-service -----------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GapDto(String requirementId, String text, String type) {
    }


    // ---- jd-service -----------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementDto(String requirementId, String text, String type, int weight, List<String> normalisedTerms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdAnalysisDto(
            String jobDescriptionId, String title, String company, String seniority,
            List<String> keywords, List<RequirementDto> requirements) {
    }

    // ---- jd-service: JD optimization (ADR-033) ----------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptimizationKeywordDto(String term, String priority, String category, List<String> evidenceIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptimizationMatchDto(
            String requirementId, List<String> evidenceIds, String matchStrength, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptimizationMissingDto(String requirementId, String note) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptimizationDataDto(
            String targetRole, String targetCompany,
            List<OptimizationKeywordDto> keywords,
            List<OptimizationMatchDto> requirementMatches,
            List<OptimizationMissingDto> missingRequirements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JdOptimizationDto(
            String id, String jobDescriptionId, OptimizationDataDto optimisation,
            List<String> citedEvidenceIds) {
    }

    // ---- profile-service --------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalInformationDto(String fullName, String headline, String email, String phone, List<String> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceDto(
            String evidenceId, String company, String title, String employmentType,
            String start, String end, boolean current, String location,
            List<String> bullets, List<String> technologies, List<String> metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileDto(PersonalInformationDto personalInformation, List<ExperienceDto> experiences) {
    }

    /** Mirrors profile-service's unified evidence shape (the same one application-service's own
     *  {@code EvidenceItem} mirrors) — used by {@code AtsScoringEngine} (ADR-040) to re-derive
     *  the same cited-evidence-per-section grouping {@code ResumeRenderService.assemble()} builds
     *  in application-service, without a second cross-service push. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceItem(
            String evidenceId, String type, String title, String organisation, String description,
            List<String> technologies, List<String> metrics, String startDate, String endDate,
            List<String> bullets) {
    }
}
