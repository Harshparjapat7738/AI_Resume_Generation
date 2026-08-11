package ai.careerforge.application.service;

import ai.careerforge.application.client.AiServiceClient;
import ai.careerforge.application.client.ClientDtos.CoverLetterContentRequest;
import ai.careerforge.application.client.ClientDtos.CoverLetterContentResponse;
import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.EvidenceSelectionRequest;
import ai.careerforge.application.client.ClientDtos.EvidenceSelectionResponse;
import ai.careerforge.application.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.application.client.ClientDtos.RequirementDto;
import ai.careerforge.application.client.ClientDtos.RequirementInput;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.CoverLetterVersion;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.CoverLetterVersionRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates grounded cover-letter generation for an {@link Application}: confirmed JD
 * analysis + the candidate's evidence &rarr; ai-service evidence selection &rarr; ai-service
 * cover-letter content &rarr; persistence as a new {@link CoverLetterVersion}, then repoints
 * {@code Application.coverLetterVersionId} at it.
 *
 * <p>Structured exactly like {@link EmailGenerationService} (ARCHITECTURE_DECISIONS.md
 * ADR-019): depends on {@link ApplicationService} for ownership and for recording the
 * resulting reference, rather than the other way around. Differs from email generation in one
 * respect — a cover letter needs the JD's <em>requirements</em> (for evidence selection), not
 * just its title/company, so this pipeline mirrors resume-service's two-Groq-call shape
 * (ADR-013) rather than email's single-shot one.
 */
@Service
public class CoverLetterGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterGenerationService.class);

    private final ApplicationService applicationService;
    private final ProfileServiceClient profileServiceClient;
    private final JdServiceClient jdServiceClient;
    private final AiServiceClient aiServiceClient;
    private final CoverLetterVersionRepository versions;
    private final ObjectMapper objectMapper;

    public CoverLetterGenerationService(ApplicationService applicationService,
                                        ProfileServiceClient profileServiceClient, JdServiceClient jdServiceClient,
                                        AiServiceClient aiServiceClient, CoverLetterVersionRepository versions,
                                        ObjectMapper objectMapper) {
        this.applicationService = applicationService;
        this.profileServiceClient = profileServiceClient;
        this.jdServiceClient = jdServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.versions = versions;
        this.objectMapper = objectMapper;
    }

    /** Generates (or regenerates) the cover letter for an application. Each call creates a
     *  new immutable {@link CoverLetterVersion} and repoints
     *  {@code Application.coverLetterVersionId} at it — the same regenerate-by-calling-again
     *  pattern resume-service and {@link EmailGenerationService} both use. */
    public CoverLetterVersion generate(String userId, String applicationId) {
        Application application = applicationService.requireOwned(userId, applicationId);
        requireCoverLetterGenerationAllowed(application);

        JdAnalysisDto analysis = fetchAnalysis(application.jobDescriptionId());
        List<EvidenceItem> evidence = fetchEvidence();

        if (evidence.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add at least one experience to your profile before generating a cover letter.");
        }
        List<RequirementDto> requirements = analysis.requirements() == null ? List.of() : analysis.requirements();
        if (requirements.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This job description has no extracted requirements to generate against.");
        }

        List<RequirementInput> requirementInputs = requirements.stream()
                .map(r -> new RequirementInput(r.requirementId(), r.text(), r.type(), r.weight()))
                .toList();

        CoverLetterContentResponse contentResponse;
        Set<String> selectedEvidenceIds;
        try {
            EvidenceSelectionResponse selectionResponse = aiServiceClient.selectEvidence(
                    new EvidenceSelectionRequest(requirementInputs, evidence, null));

            selectedEvidenceIds = collectEvidenceIds(readMatches(selectionResponse.selection()));

            if (selectedEvidenceIds.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "None of your profile evidence matches this job description's requirements.");
            }

            contentResponse = aiServiceClient.generateCoverLetter(
                    new CoverLetterContentRequest(analysis.title(), analysis.company(), analysis.seniority(),
                            requirementInputs, List.copyOf(selectedEvidenceIds), evidence, null));
        } catch (FeignException.BadGateway | FeignException.ServiceUnavailable ex) {
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        } catch (FeignException ex) {
            log.warn("Cover-letter generation failed for applicationId={}: {}", applicationId, ex.getMessage());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        }

        Map<String, Object> content = toMap(contentResponse.content());
        Map<String, Object> grounding = toMap(contentResponse.grounding());
        List<String> removedParagraphs = contentResponse.removedParagraphs() == null
                ? List.of() : contentResponse.removedParagraphs();

        int nextVersion = versions
                .findTopByApplicationIdAndUserIdOrderByVersionDesc(applicationId, userId)
                .map(v -> v.version() + 1)
                .orElse(1);

        CoverLetterVersion saved = versions.save(new CoverLetterVersion(
                applicationId, userId, application.jobDescriptionId(),
                analysis.title(), analysis.company(), nextVersion,
                content, grounding, removedParagraphs,
                provenanceField(contentResponse, "promptVersion"),
                provenanceField(contentResponse, "model")));

        applicationService.attachCoverLetter(userId, applicationId, saved.id());

        return saved;
    }

    /** Latest version — 404 if the application has never had a cover letter generated. */
    public CoverLetterVersion requireLatest(String userId, String applicationId) {
        applicationService.requireOwned(userId, applicationId);
        return versions.findTopByApplicationIdAndUserIdOrderByVersionDesc(applicationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- guards ----

    private void requireCoverLetterGenerationAllowed(Application application) {
        GenerationType type = application.generationType();
        if (type != GenerationType.COVER_LETTER_ONLY && type != GenerationType.ALL) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This application's generation type does not include cover-letter generation.");
        }
    }

    // ------------------------------------------------------------- upstream calls ----

    private JdAnalysisDto fetchAnalysis(String jobDescriptionId) {
        try {
            return jdServiceClient.getAnalysis(jobDescriptionId);
        } catch (FeignException.NotFound ex) {
            throw ApiException.notOwned();
        } catch (FeignException.Conflict ex) {
            throw new ApiException(ErrorCode.JD_NOT_CONFIRMED);
        } catch (FeignException ex) {
            log.warn("jd-service call failed for jobDescriptionId={}: {}", jobDescriptionId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private List<EvidenceItem> fetchEvidence() {
        try {
            return profileServiceClient.getEvidence();
        } catch (FeignException ex) {
            log.warn("profile-service call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    // ---------------------------------------------------------------- json helpers ----

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMatches(JsonNode selection) {
        if (selection == null || !selection.has("matches")) {
            return List.of();
        }
        Map<String, Object> asMap = objectMapper.convertValue(selection, new TypeReference<Map<String, Object>>() { });
        Object matches = asMap.get("matches");
        return matches instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
    }

    private Set<String> collectEvidenceIds(List<Map<String, Object>> matches) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> match : matches) {
            Object evidenceIds = match.get("evidenceIds");
            if (evidenceIds instanceof List<?> list) {
                for (Object id : list) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() { });
    }

    private String provenanceField(CoverLetterContentResponse response, String field) {
        JsonNode provenance = response.provenance();
        if (provenance == null || !provenance.hasNonNull(field)) {
            return null;
        }
        return provenance.get(field).asText();
    }
}
