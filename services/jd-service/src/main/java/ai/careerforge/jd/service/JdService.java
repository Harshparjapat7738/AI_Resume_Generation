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
import ai.careerforge.jd.domain.JobDescriptionStatus;
import ai.careerforge.jd.domain.Requirement;
import ai.careerforge.jd.repository.JdAnalysisRepository;
import ai.careerforge.jd.repository.JdVersionRepository;
import ai.careerforge.jd.repository.JobDescriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class JdService {

    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private final JobDescriptionRepository jobDescriptions;
    private final JdVersionRepository jdVersions;
    private final JdAnalysisRepository jdAnalyses;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    public JdService(JobDescriptionRepository jobDescriptions, JdVersionRepository jdVersions,
                     JdAnalysisRepository jdAnalyses, AiServiceClient aiServiceClient,
                     ObjectMapper objectMapper) {
        this.jobDescriptions = jobDescriptions;
        this.jdVersions = jdVersions;
        this.jdAnalyses = jdAnalyses;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
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

    public JobDescription requireOwned(String userId, String id) {
        return jobDescriptions.findByIdAndUserId(id, userId).orElseThrow(ApiException::notOwned);
    }

    public JdVersion currentVersion(JobDescription jd) {
        return jdVersions.findByJobDescriptionIdAndVersion(jd.id(), jd.currentVersion())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
    }

    public JobDescription confirm(String userId, String id) {
        JobDescription jd = requireOwned(userId, id);
        if (jd.status() != JobDescriptionStatus.CONFIRMED) {
            jd.confirm();
            jd = jobDescriptions.save(jd);
        }
        return jd;
    }

    /** Computes (or returns the cached) analysis. Requires the JD to already be confirmed. */
    public JdAnalysis analyse(String userId, String id) {
        JobDescription jd = requireOwned(userId, id);
        if (jd.status() != JobDescriptionStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.JD_NOT_CONFIRMED);
        }

        JdVersion version = jdVersions.findByJobDescriptionIdAndVersion(jd.id(), jd.confirmedVersion())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));

        return jdAnalyses.findByJdVersionId(version.id()).orElseGet(() -> runAnalysis(jd, version));
    }

    private JdAnalysis runAnalysis(JobDescription jd, JdVersion version) {
        JdAnalysisResponse response = aiServiceClient.analyseJd(
                new JdAnalysisRequest(version.rawText(), null));

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
