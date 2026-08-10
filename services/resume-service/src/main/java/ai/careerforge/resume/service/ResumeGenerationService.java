package ai.careerforge.resume.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.resume.client.AiServiceClient;
import ai.careerforge.resume.client.ClientDtos.EvidenceItem;
import ai.careerforge.resume.client.ClientDtos.EvidenceSelectionRequest;
import ai.careerforge.resume.client.ClientDtos.EvidenceSelectionResponse;
import ai.careerforge.resume.client.ClientDtos.JdAnalysis;
import ai.careerforge.resume.client.ClientDtos.Requirement;
import ai.careerforge.resume.client.ClientDtos.RequirementInput;
import ai.careerforge.resume.client.ClientDtos.ResumeContentRequest;
import ai.careerforge.resume.client.ClientDtos.ResumeContentResponse;
import ai.careerforge.resume.client.JdServiceClient;
import ai.careerforge.resume.client.ProfileServiceClient;
import ai.careerforge.resume.domain.ResumeGeneration;
import ai.careerforge.resume.domain.ResumeVersion;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.repository.ResumeGenerationRepository;
import ai.careerforge.resume.repository.ResumeVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates generation: evidence + confirmed JD analysis &rarr; ai-service evidence
 * selection &rarr; ai-service resume content &rarr; persistence.
 *
 * <p><strong>Deviation from docs/API_CATALOG.md (ARCHITECTURE_DECISIONS.md ADR-013):</strong>
 * this runs synchronously inside the HTTP request rather than as an async job polled via
 * {@code GET /api/resumes/generations/{jobId}} — there is no Redis Streams worker in this
 * milestone slice. Two sequential Groq calls typically complete in single-digit seconds.
 */
@Service
public class ResumeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ResumeGenerationService.class);

    private final ProfileServiceClient profileServiceClient;
    private final JdServiceClient jdServiceClient;
    private final AiServiceClient aiServiceClient;
    private final ResumeGenerationRepository generations;
    private final ResumeVersionRepository versions;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    public ResumeGenerationService(ProfileServiceClient profileServiceClient, JdServiceClient jdServiceClient,
                                   AiServiceClient aiServiceClient, ResumeGenerationRepository generations,
                                   ResumeVersionRepository versions, TemplateService templateService,
                                   ObjectMapper objectMapper) {
        this.profileServiceClient = profileServiceClient;
        this.jdServiceClient = jdServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.generations = generations;
        this.versions = versions;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    public ResumeVersion generate(String userId, String jobDescriptionId, String templateId) {
        // Resolved first and deliberately before any AI call: an invalid or unauthorized
        // template must fail fast without spending a Groq request.
        Template template = templateService.resolveForGeneration(userId, templateId);

        JdAnalysis analysis = fetchAnalysis(jobDescriptionId);
        List<EvidenceItem> evidence = fetchEvidence();

        if (evidence.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add at least one experience to your profile before generating a resume.");
        }
        List<Requirement> requirements = analysis.requirements() == null ? List.of() : analysis.requirements();
        if (requirements.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This job description has no extracted requirements to generate against.");
        }

        ResumeGeneration generation = generations.save(
                new ResumeGeneration(userId, jobDescriptionId, template.id(), template.version()));

        List<RequirementInput> requirementInputs = requirements.stream()
                .map(r -> new RequirementInput(r.requirementId(), r.text(), r.type(), r.weight()))
                .toList();

        try {
            EvidenceSelectionResponse selectionResponse = aiServiceClient.selectEvidence(
                    new EvidenceSelectionRequest(requirementInputs, evidence, null));

            List<Map<String, Object>> matches = readMatches(selectionResponse.selection());
            Set<String> selectedEvidenceIds = collectEvidenceIds(matches);

            if (selectedEvidenceIds.isEmpty()) {
                generation.fail("NO_EVIDENCE_MATCHED");
                generations.save(generation);
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "None of your profile evidence matches this job description's requirements.");
            }

            ResumeContentResponse contentResponse = aiServiceClient.generateContent(new ResumeContentRequest(
                    analysis.title(), analysis.seniority(), requirementInputs,
                    List.copyOf(selectedEvidenceIds), evidence, null));

            Map<String, Object> content = toMap(contentResponse.content());
            Map<String, Object> grounding = toMap(contentResponse.grounding());
            List<String> removedSections = contentResponse.removedSections() == null
                    ? List.of() : contentResponse.removedSections();
            List<Map<String, Object>> gaps = computeGaps(requirements, matches);

            ResumeVersion version = new ResumeVersion(
                    generation.id(), userId, jobDescriptionId, analysis.title(), analysis.company(), 1,
                    template.id(), template.version(),
                    content, matches, gaps, grounding, removedSections,
                    provenanceField(contentResponse, "promptVersion"),
                    provenanceField(contentResponse, "model"));
            version = versions.save(version);

            generation.complete();
            generations.save(generation);

            return version;
        } catch (FeignException.BadGateway | FeignException.ServiceUnavailable ex) {
            generation.fail("AI_GENERATION_FAILED");
            generations.save(generation);
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        } catch (FeignException ex) {
            generation.fail("AI_GENERATION_FAILED");
            generations.save(generation);
            log.warn("Generation failed for jobDescriptionId={}: {}", jobDescriptionId, ex.getMessage());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    public ResumeVersion requireOwned(String userId, String id) {
        return versions.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /** Requirements the AI could not connect to any evidence — surfaced as gaps, never invented. */
    private List<Map<String, Object>> computeGaps(List<Requirement> requirements, List<Map<String, Object>> matches) {
        Set<String> matchedRequirementIds = new LinkedHashSet<>();
        for (Map<String, Object> match : matches) {
            Object reqId = match.get("requirementId");
            Object strength = match.get("matchStrength");
            if (reqId != null && strength != null && !"NONE".equals(strength)) {
                matchedRequirementIds.add(reqId.toString());
            }
        }
        return requirements.stream()
                .filter(r -> !matchedRequirementIds.contains(r.requirementId()))
                .map(r -> {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("requirementId", r.requirementId());
                    gap.put("text", r.text());
                    gap.put("type", r.type());
                    return gap;
                })
                .toList();
    }

    private JdAnalysis fetchAnalysis(String jobDescriptionId) {
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

    private String provenanceField(ResumeContentResponse response, String field) {
        JsonNode provenance = response.provenance();
        if (provenance == null || !provenance.hasNonNull(field)) {
            return null;
        }
        return provenance.get(field).asText();
    }
}
