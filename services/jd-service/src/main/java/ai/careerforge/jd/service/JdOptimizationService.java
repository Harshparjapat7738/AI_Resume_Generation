package ai.careerforge.jd.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.jd.client.AiClientDtos.EvidenceItem;
import ai.careerforge.jd.client.AiClientDtos.JdOptimizationRequest;
import ai.careerforge.jd.client.AiClientDtos.JdOptimizationResponse;
import ai.careerforge.jd.client.AiClientDtos.RequirementInput;
import ai.careerforge.jd.client.AiServiceClient;
import ai.careerforge.jd.client.ProfileServiceClient;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.JdOptimization;
import ai.careerforge.jd.domain.JobDescription;
import ai.careerforge.jd.repository.JdOptimizationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates JD optimization (ADR-033): JD analysis (against the JD's current text — no
 * confirm step, ADR-037) + verified evidence &rarr; ai-service &rarr; persisted
 * {@link JdOptimization}. The successor to resume-service's generation orchestration, and
 * deliberately the same shape — resolve inputs, make one AI call, persist the validated result —
 * minus the two-stage content pipeline, because there is no content to write.
 *
 * <p>Lives in jd-service because everything it needs is already here: the job description, its
 * ownership check, and its cached analysis. The only new dependency is profile-service for the
 * evidence inventory. Nothing about the JD or the profile is copied into the stored result — see
 * {@link JdOptimization}.
 *
 * <p>Runs synchronously inside the request, matching how JD analysis and (formerly) resume
 * generation already behaved (ADR-013): one Groq call, typically single-digit seconds.
 */
@Service
public class JdOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(JdOptimizationService.class);

    private final JdService jdService;
    private final ProfileServiceClient profileServiceClient;
    private final AiServiceClient aiServiceClient;
    private final JdOptimizationRepository optimizations;
    private final ObjectMapper objectMapper;

    public JdOptimizationService(JdService jdService, ProfileServiceClient profileServiceClient,
                                 AiServiceClient aiServiceClient, JdOptimizationRepository optimizations,
                                 ObjectMapper objectMapper) {
        this.jdService = jdService;
        this.profileServiceClient = profileServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.optimizations = optimizations;
        this.objectMapper = objectMapper;
    }

    /**
     * Optimises the candidate's profile against the job description's current text.
     *
     * <p>{@code refresh=false} returns an existing optimization for the same JD version rather
     * than spending another AI request — the same read-through caching {@code JdService#analyse}
     * already applies to the analysis. {@code refresh=true} recomputes and replaces it, which is
     * what the user wants after editing their profile: the JD text is unchanged, but the
     * evidence it is being matched against is not.
     *
     * @throws ApiException {@code NOT_FOUND} if it isn't the caller's, {@code VALIDATION_ERROR}
     *         if there is nothing to optimise against, {@code UPSTREAM_UNAVAILABLE}/
     *         {@code AI_GENERATION_FAILED} if a dependency fails
     */
    public JdOptimization optimise(String userId, String jobDescriptionId, boolean refresh) {
        // Ownership is enforced here, once, by the same method the analysis endpoint uses —
        // never re-implemented.
        JdAnalysis analysis = jdService.analyse(userId, jobDescriptionId);
        JobDescription jd = jdService.requireOwned(userId, jobDescriptionId);

        if (!refresh) {
            var existing = optimizations.findByJdVersionId(analysis.jdVersionId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        List<RequirementInput> requirements = analysis.requirements() == null ? List.of()
                : analysis.requirements().stream()
                        .map(r -> new RequirementInput(r.requirementId(), r.text(), r.type(), r.weight()))
                        .toList();
        if (requirements.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This job description has no extracted requirements to optimise against.");
        }

        List<EvidenceItem> evidence = fetchEvidence();
        if (evidence.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add at least one experience to your profile before optimising for a job.");
        }

        JdOptimizationResponse response = callAi(new JdOptimizationRequest(
                analysis.title(), analysis.company(), analysis.seniority(),
                requirements, analysis.keywords(), evidence, null));

        Map<String, Object> optimisation = toMap(response.optimisation());
        List<String> citedEvidenceIds = citedEvidenceIds(response.optimisation());

        return optimizations.findByJdVersionId(analysis.jdVersionId())
                .map(existing -> {
                    existing.replaceWith(optimisation, citedEvidenceIds,
                            textOrNull(response.provenance(), "promptVersion"),
                            textOrNull(response.provenance(), "model"));
                    return optimizations.save(existing);
                })
                .orElseGet(() -> optimizations.save(new JdOptimization(
                        userId, jd.id(), analysis.jdVersionId(), optimisation, citedEvidenceIds,
                        textOrNull(response.provenance(), "promptVersion"),
                        textOrNull(response.provenance(), "model"))));
    }

    /** A stored optimization, scoped to its owner — someone else's is reported not-found, never
     *  forbidden (ADR-007). */
    public JdOptimization requireOwned(String userId, String id) {
        return optimizations.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /** The current optimization for a job description, if one has been computed. */
    public java.util.Optional<JdOptimization> findForJobDescription(String userId, String jobDescriptionId) {
        JdAnalysis analysis = jdService.analyse(userId, jobDescriptionId);
        return optimizations.findByJdVersionId(analysis.jdVersionId());
    }

    // ------------------------------------------------------------------ helpers ----

    private JdOptimizationResponse callAi(JdOptimizationRequest request) {
        try {
            return aiServiceClient.optimise(request);
        } catch (FeignException.TooManyRequests ex) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "The AI provider's rate limit was reached. Please wait a minute and retry.");
        } catch (FeignException ex) {
            // Never surface the provider's own body — it can echo prompt content.
            log.warn("JD optimization failed: {}", ex.status());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    private List<EvidenceItem> fetchEvidence() {
        try {
            return profileServiceClient.getEvidence();
        } catch (FeignException ex) {
            log.warn("profile-service call failed: {}", ex.status());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    /** Every evidence id the result cites, in first-seen order. Mirrors ai-service's own
     *  {@code JdOptimizationService.citedEvidenceIds} — recomputed here rather than trusted from
     *  the wire, so what gets persisted as provenance is derived from the payload actually
     *  stored. */
    private List<String> citedEvidenceIds(JsonNode optimisation) {
        Set<String> ids = new LinkedHashSet<>();
        if (optimisation == null) {
            return List.of();
        }
        for (JsonNode keyword : optimisation.path("keywords")) {
            keyword.path("evidenceIds").forEach(id -> ids.add(id.asText()));
        }
        for (JsonNode match : optimisation.path("requirementMatches")) {
            match.path("evidenceIds").forEach(id -> ids.add(id.asText()));
        }
        for (JsonNode item : optimisation.path("emphasis")) {
            String id = item.path("evidenceId").asText(null);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() { });
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
