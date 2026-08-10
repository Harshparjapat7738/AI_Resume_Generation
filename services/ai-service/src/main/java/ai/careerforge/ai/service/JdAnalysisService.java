package ai.careerforge.ai.service;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.client.GroqClient;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.prompt.UntrustedContent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * Semantic understanding of a job description.
 *
 * <p>The JD is the most hostile input the platform accepts: arbitrary text pulled from the
 * open web. It is sanitised and fenced here, described as data by the system prompt, and
 * constrained by a schema on the way out.
 */
@Service
public class JdAnalysisService {

    private static final String PROMPT = "jd-analysis";
    private static final String SCHEMA = "jd-analysis.schema.json";
    private static final int MAX_JD_CHARS = 40_000;

    private final GroqClient groqClient;
    private final AiGenerationSupport support;

    public JdAnalysisService(GroqClient groqClient, AiGenerationSupport support) {
        this.groqClient = groqClient;
        this.support = support;
    }

    public AiResponses.JdAnalysisResponse analyse(AiRequests.JdAnalysisRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());

        String fenced = UntrustedContent.fence(
                "JOB_DESCRIPTION", request.jobDescriptionText(), MAX_JD_CHARS);

        GroqClient.GroqResult result = groqClient.complete(prompt.body(), fenced, PROMPT);
        JsonNode analysis = support.validateSchema(result.content(), SCHEMA, PROMPT);

        return new AiResponses.JdAnalysisResponse(analysis,
                new AiResponses.Provenance(prompt.versionLabel(), result.model(),
                        result.totalTokens(), false));
    }
}
