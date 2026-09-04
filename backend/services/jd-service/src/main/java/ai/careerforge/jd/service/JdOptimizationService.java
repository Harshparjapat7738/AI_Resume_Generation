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
import ai.careerforge.jd.domain.Requirement;
import ai.careerforge.jd.repository.JdOptimizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates JD optimization (ADR-033, restructured by ADR-038): JD analysis (against the
 * JD's current text — no confirm step, ADR-037) + deterministically pre-filtered evidence &rarr;
 * one adjudication call to ai-service &rarr; deterministic merge &rarr; persisted
 * {@link JdOptimization}.
 *
 * <p><strong>Call budget (ADR-038):</strong> at most 2 Groq calls for a first-time ("cold")
 * optimization of a JD that hasn't been analysed before (analysis + adjudication), exactly 1 for
 * a "warm" one (analysis already cached, only adjudication runs), and — when every requirement
 * turns out to have no plausible evidence candidate at all — 0, since there is nothing left for
 * an adjudication call to judge. Re-checking after a profile edit ({@code refresh=true}) never
 * re-runs analysis (unaffected by an evidence change) and reuses the exact same path. Adding one
 * missing skill from the Skill Gap screen costs 0 Groq calls — see {@link #patchWithLatestEvidence}.
 *
 * <p>Lives in jd-service because everything it needs is already here: the job description, its
 * ownership check, and its cached analysis. The only new dependency is profile-service for the
 * evidence inventory. Nothing about the JD or the profile is copied into the stored result — see
 * {@link JdOptimization}.
 */
@Service
public class JdOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(JdOptimizationService.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(45);
    private static final Duration LOCK_POLL_TIMEOUT = Duration.ofSeconds(30);

    private final JdService jdService;
    private final ProfileServiceClient profileServiceClient;
    private final AiServiceClient aiServiceClient;
    private final JdOptimizationRepository optimizations;
    private final ObjectMapper objectMapper;
    private final EvidenceMatcher evidenceMatcher;
    private final OptimizationMerge optimizationMerge;
    private final SingleFlightLock singleFlightLock;

    public JdOptimizationService(JdService jdService, ProfileServiceClient profileServiceClient,
                                 AiServiceClient aiServiceClient, JdOptimizationRepository optimizations,
                                 ObjectMapper objectMapper, EvidenceMatcher evidenceMatcher,
                                 OptimizationMerge optimizationMerge, SingleFlightLock singleFlightLock) {
        this.jdService = jdService;
        this.profileServiceClient = profileServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.optimizations = optimizations;
        this.objectMapper = objectMapper;
        this.evidenceMatcher = evidenceMatcher;
        this.optimizationMerge = optimizationMerge;
        this.singleFlightLock = singleFlightLock;
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

        if (analysis.requirements().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This job description has no extracted requirements to optimise against.");
        }
        List<EvidenceItem> evidence = fetchEvidence();
        if (evidence.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add at least one experience to your profile before optimising for a job.");
        }

        // Single-flight (ADR-038): a double-click or a second tab on the same JD must not race
        // a second full analysis+adjudication pipeline against this one.
        String lockKey = "jd-optimize:" + analysis.jdVersionId();
        return singleFlightLock.withLock(lockKey, LOCK_TTL, LOCK_POLL_TIMEOUT,
                () -> refresh ? Optional.empty() : optimizations.findByJdVersionId(analysis.jdVersionId()),
                () -> computeAndPersist(userId, jd, analysis, evidence));
    }

    private JdOptimization computeAndPersist(String userId, JobDescription jd, JdAnalysis analysis,
            List<EvidenceItem> evidence) {
        EvidenceMatcher.FilterResult filter = evidenceMatcher.filter(analysis.requirements(), evidence);

        JsonNode adjudicationMatches = objectMapper.createArrayNode();
        String promptVersion = null;
        String model = null;

        if (!filter.selectedEvidence().isEmpty()) {
            List<Requirement> requirementsWithCandidates = analysis.requirements().stream()
                    .filter(r -> !filter.zeroCandidateRequirementIds().contains(r.requirementId()))
                    .toList();

            List<RequirementInput> requirementInputs = requirementsWithCandidates.stream()
                    .map(r -> new RequirementInput(r.requirementId(), r.text(), r.type(), r.weight()))
                    .toList();

            JdOptimizationResponse response = callAi(new JdOptimizationRequest(
                    analysis.title(), analysis.company(), analysis.seniority(),
                    requirementInputs, List.of(), filter.selectedEvidence(), null));
            adjudicationMatches = response.optimisation().path("matches");
            promptVersion = textOrNull(response.provenance(), "promptVersion");
            model = textOrNull(response.provenance(), "model");
        } else {
            log.info("Skipping the adjudication call entirely — every requirement is a "
                    + "deterministic gap (no evidence candidate at all) for jobDescriptionId={}", jd.id());
        }

        Map<String, Object> optimisation = optimizationMerge.merge(
                analysis, adjudicationMatches, filter.zeroCandidateRequirementIds(), evidence);
        optimisation.put("derived", false);
        optimisation.put("stale", false);

        List<String> citedEvidenceIds = OptimizationMerge.citedEvidenceIds(optimisation);
        String finalPromptVersion = promptVersion;
        String finalModel = model;

        return optimizations.findByJdVersionId(analysis.jdVersionId())
                .map(existing -> {
                    existing.replaceWith(optimisation, citedEvidenceIds, finalPromptVersion, finalModel);
                    return optimizations.save(existing);
                })
                .orElseGet(() -> optimizations.save(new JdOptimization(
                        userId, jd.id(), analysis.jdVersionId(), optimisation, citedEvidenceIds,
                        finalPromptVersion, finalModel)));
    }

    /**
     * Deterministic skill-gap patch (ADR-038) — <strong>zero Groq calls</strong>. Re-fetches the
     * profile's current evidence (now including whatever was just added), re-runs the same
     * lexical filtering {@link EvidenceMatcher} always does, and promotes any requirement that
     * was previously a gap but now has a lexical candidate into a match citing that candidate —
     * conservatively, as {@code PARTIAL}, never re-claiming {@code STRONG} (only a real
     * adjudication call could justify that). Every previously-established match is carried
     * forward completely unchanged. The result is marked {@code derived}/{@code stale}: it
     * reflects a real, current gap check, but the promoted entries were never actually judged by
     * the model — a user who wants that judgement can trigger a full {@code refresh=true}
     * optimise, which this patch never replaces.
     *
     * @throws ApiException {@code VALIDATION_ERROR} if no optimization exists yet to patch —
     *         the ordinary "Identify Skill Gaps" flow always creates one first
     */
    public JdOptimization patchWithLatestEvidence(String userId, String jobDescriptionId) {
        JdAnalysis analysis = jdService.analyse(userId, jobDescriptionId);
        JobDescription jd = jdService.requireOwned(userId, jobDescriptionId);

        JdOptimization existing = optimizations.findByJdVersionId(analysis.jdVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Run Identify Skill Gaps before adding individual skills."));

        List<EvidenceItem> freshEvidence = fetchEvidence();
        EvidenceMatcher.FilterResult freshFilter = evidenceMatcher.filter(analysis.requirements(), freshEvidence);

        Set<String> alreadyMatchedRequirementIds = new HashSet<>();
        ArrayNode syntheticMatches = objectMapper.createArrayNode();
        for (Object rawMatch : existingRequirementMatches(existing.optimisation())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> match = (Map<String, Object>) rawMatch;
            String requirementId = (String) match.get("requirementId");
            alreadyMatchedRequirementIds.add(requirementId);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("requirementId", requirementId);
            node.put("matchKind", (String) match.get("matchStrength"));
            ArrayNode ids = node.putArray("evidenceIds");
            @SuppressWarnings("unchecked")
            List<String> evidenceIds = (List<String>) match.getOrDefault("evidenceIds", List.of());
            evidenceIds.forEach(ids::add);
            syntheticMatches.add(node);
        }

        int promoted = 0;
        for (Requirement requirement : analysis.requirements()) {
            if (alreadyMatchedRequirementIds.contains(requirement.requirementId())) {
                continue;
            }
            if (freshFilter.zeroCandidateRequirementIds().contains(requirement.requirementId())) {
                continue;
            }
            List<String> candidates = freshFilter.candidatesByRequirement()
                    .getOrDefault(requirement.requirementId(), List.of());
            if (candidates.isEmpty()) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("requirementId", requirement.requirementId());
            node.put("matchKind", "PARTIAL");
            ArrayNode ids = node.putArray("evidenceIds");
            candidates.stream().limit(3).forEach(ids::add);
            syntheticMatches.add(node);
            promoted++;
        }

        Map<String, Object> optimisation = optimizationMerge.merge(
                analysis, syntheticMatches, freshFilter.zeroCandidateRequirementIds(), freshEvidence);
        optimisation.put("derived", true);
        optimisation.put("stale", true);

        log.info("Deterministic skill-gap patch for jobDescriptionId={}: promoted {} requirement(s) "
                + "from missing to matched, zero Groq calls", jd.id(), promoted);

        List<String> citedEvidenceIds = OptimizationMerge.citedEvidenceIds(optimisation);
        existing.replaceWith(optimisation, citedEvidenceIds, existing.promptVersion(), existing.modelId());
        return optimizations.save(existing);
    }

    @SuppressWarnings("unchecked")
    private List<Object> existingRequirementMatches(Map<String, Object> optimisation) {
        Object raw = optimisation == null ? null : optimisation.get("requirementMatches");
        return raw instanceof List<?> list ? (List<Object>) list : List.of();
    }

    /** A stored optimization, scoped to its owner — someone else's is reported not-found, never
     *  forbidden (ADR-007). */
    public JdOptimization requireOwned(String userId, String id) {
        return optimizations.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /** The current optimization for a job description, if one has been computed. */
    public Optional<JdOptimization> findForJobDescription(String userId, String jobDescriptionId) {
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

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
