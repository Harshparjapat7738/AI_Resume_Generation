package ai.careerforge.jd.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.jd.client.AiClientDtos.JdAnalysisRequest;
import ai.careerforge.jd.client.AiClientDtos.JdAnalysisResponse;
import ai.careerforge.jd.client.AiServiceClient;
import ai.careerforge.jd.client.JdAnalysisPayload;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.JdVersion;
import ai.careerforge.jd.domain.JobDescription;
import ai.careerforge.jd.domain.Requirement;
import ai.careerforge.jd.fetch.JdUrlFetcher;
import ai.careerforge.jd.fetch.JdUrlFetcher.FetchedPage;
import ai.careerforge.jd.fetch.JobPostingExtractor;
import ai.careerforge.jd.fetch.JobPostingExtractor.ExtractedJd;
import ai.careerforge.jd.repository.JdAnalysisRepository;
import ai.careerforge.jd.repository.JdVersionRepository;
import ai.careerforge.jd.repository.JobDescriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JdService {

    private static final Logger log = LoggerFactory.getLogger(JdService.class);

    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final int MIN_JD_LENGTH = 50;
    private static final int MAX_JD_LENGTH = 60_000;

    private final JobDescriptionRepository jobDescriptions;
    private final JdVersionRepository jdVersions;
    private final JdAnalysisRepository jdAnalyses;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final JdUrlFetcher urlFetcher;
    private final JobPostingExtractor jobPostingExtractor;

    public JdService(JobDescriptionRepository jobDescriptions, JdVersionRepository jdVersions,
                     JdAnalysisRepository jdAnalyses, AiServiceClient aiServiceClient,
                     ObjectMapper objectMapper, JdUrlFetcher urlFetcher,
                     JobPostingExtractor jobPostingExtractor) {
        this.jobDescriptions = jobDescriptions;
        this.jdVersions = jdVersions;
        this.jdAnalyses = jdAnalyses;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
        this.urlFetcher = urlFetcher;
        this.jobPostingExtractor = jobPostingExtractor;
    }

    public record Submission(JobDescription jobDescription, JdVersion version) {
    }

    public Submission submitText(String userId, String rawText) {
        JobDescription jd = new JobDescription(userId, "TEXT");
        jd = jobDescriptions.save(jd);

        String normalised = normalise(rawText);
        JdVersion version = new JdVersion(jd.id(), jd.currentVersion(), rawText, normalised, "TEXT_INPUT");
        version = jdVersions.save(version);

        return new Submission(jd, version);
    }

    /**
     * Fetches a job posting URL through the SSRF guard, extracts it (structured JSON-LD if
     * the page has it, readable text either way), and stores it exactly like a pasted JD —
     * confirmation and analysis proceed identically regardless of source (see
     * ARCHITECTURE_DECISIONS.md ADR-015).
     */
    public Submission fetchUrl(String userId, String rawUrl) {
        FetchedPage page = urlFetcher.fetch(rawUrl);
        ExtractedJd extracted = jobPostingExtractor.extract(page.html());

        String rawText = extracted.rawText() == null ? "" : extracted.rawText();
        if (rawText.length() < MIN_JD_LENGTH) {
            throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                    "Unable to extract this job description from this URL.");
        }
        if (rawText.length() > MAX_JD_LENGTH) {
            rawText = rawText.substring(0, MAX_JD_LENGTH);
        }

        JobDescription jd = new JobDescription(userId, "URL", page.finalUrl().toString());
        jd.applyUrlExtractionPreview(
                extracted.title(), extracted.company(), extracted.location(),
                extracted.skillsSummary(), extracted.experienceSummary());
        jd = jobDescriptions.save(jd);

        String normalised = normalise(rawText);
        JdVersion version = new JdVersion(jd.id(), jd.currentVersion(), rawText, normalised, extracted.extractionMethod());
        version = jdVersions.save(version);

        return new Submission(jd, version);
    }

    public JobDescription requireOwned(String userId, String id) {
        return jobDescriptions.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    /**
     * Edits the JD's text — no confirm/review gate exists any more (the workflow analyses and
     * optimises directly against whatever text is currently submitted), so this is unconditional.
     * Creates a new, immutable {@link JdVersion} rather than mutating the existing one (see that
     * class's own comment) and advances {@code currentVersion} to point at it — a JD version that
     * was ever the basis for an analysis is never altered after the fact.
     */
    public JobDescription editText(String userId, String id, String rawText) {
        JobDescription jd = requireOwned(userId, id);

        String normalised = normalise(rawText);
        int nextVersion = jd.currentVersion() + 1;
        jdVersions.save(new JdVersion(jd.id(), nextVersion, rawText, normalised, "TEXT_EDIT"));
        jd.applyEdit(nextVersion);
        return jobDescriptions.save(jd);
    }

    public JdVersion currentVersion(JobDescription jd) {
        return jdVersions.findByJobDescriptionIdAndVersion(jd.id(), jd.currentVersion())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
    }

    /** Computes (or returns the cached) analysis for the JD's current text — no confirm step. */
    public JdAnalysis analyse(String userId, String id) {
        JobDescription jd = requireOwned(userId, id);

        JdVersion version = jdVersions.findByJobDescriptionIdAndVersion(jd.id(), jd.currentVersion())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));

        return jdAnalyses.findByJdVersionId(version.id()).orElseGet(() -> runAnalysis(jd, version));
    }

    private JdAnalysis runAnalysis(JobDescription jd, JdVersion version) {
        JdAnalysisResponse response;
        try {
            response = aiServiceClient.analyseJd(new JdAnalysisRequest(version.rawText(), null));
        } catch (FeignException.BadGateway | FeignException.ServiceUnavailable ex) {
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        } catch (FeignException ex) {
            log.warn("JD analysis failed for jobDescriptionId={}: {}", jd.id(), ex.getMessage());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        }

        JdAnalysisPayload payload = readPayload(response.analysis());

        if (!payload.isJobPosting()) {
            throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                    payload.notReason() != null ? payload.notReason()
                            : "The supplied text does not appear to be a job description.");
        }

        List<Requirement> requirements = payload.requirements() == null ? List.of()
                : payload.requirements().stream()
                        .map(r -> new Requirement(r.requirementId(), r.text(), r.type(), r.weight(), r.normalisedTerms()))
                        .toList();

        String promptVersion = textOrNull(response.provenance(), "promptVersion");
        String model = textOrNull(response.provenance(), "model");

        jd.applyAnalysedTitleAndCompany(payload.jobTitle(), payload.company());
        jobDescriptions.save(jd);

        JdAnalysis analysis = new JdAnalysis(jd.id(), version.id(), payload.jobTitle(), payload.company(),
                payload.seniority(), payload.keywords(), requirements, promptVersion, model);
        return jdAnalyses.save(analysis);
    }

    private JdAnalysisPayload readPayload(JsonNode analysisNode) {
        try {
            return objectMapper.treeToValue(analysisNode, JdAnalysisPayload.class);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED,
                    "The job description could not be analysed. Please try again.");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private static String normalise(String text) {
        String collapsedSpaces = WHITESPACE.matcher(text).replaceAll(" ");
        String collapsedBlankLines = BLANK_LINES.matcher(collapsedSpaces).replaceAll("\n\n");
        return collapsedBlankLines.trim();
    }
}
