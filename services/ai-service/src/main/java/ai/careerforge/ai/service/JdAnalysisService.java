package ai.careerforge.ai.service;

import ai.careerforge.ai.api.dto.AiRequests;
import ai.careerforge.ai.api.dto.AiResponses;
import ai.careerforge.ai.client.AiChatClient;
import ai.careerforge.ai.prompt.PromptRegistry;
import ai.careerforge.ai.prompt.UntrustedContent;
import org.springframework.stereotype.Service;

/**
 * Semantic understanding of a job description.
 *
 * <p>The JD is the most hostile input the platform accepts: arbitrary text pulled from the
 * open web. It is sanitised and fenced here, described as data by the system prompt, and
 * constrained by a schema on the way out.
 *
 * <p><strong>Groq only</strong> (ADR-033): this call site
 * briefly went through {@code AiProviderRouter} (Gemini-primary, Groq-fallback); that routing
 * was removed platform-wide — every JSON/content-generation operation (JD Analysis, Evidence
 * Selection, Resume Content, Cover Letter Content, Email Content) now injects
 * {@link AiChatClient} directly, exactly as {@code EmailContentService} always has, resolving
 * unambiguously to {@link ai.careerforge.ai.client.GroqClient}. Gemini was removed entirely (ADR-033). Nothing else about this class changed: same prompt, same schema, same
 * {@link AiGenerationSupport#validateSchema} call, same {@link AiResponses.JdAnalysisResponse}/
 * {@link AiResponses.Provenance} shape, same caller ({@code JdService}), same caching and persistence.
 */
@Service
public class JdAnalysisService {

    private static final String PROMPT = "jd-analysis";
    private static final String SCHEMA = "jd-analysis.schema.json";
    private static final int MAX_JD_CHARS = 40_000;
    /** ADR-038: this call's schema caps requirements/keywords tightly (v2 prompt), so it never
     *  legitimately needs the old blanket 4,096 — reserving that much at admission wasted more
     *  than half the account's whole per-minute token budget on one call for no benefit. */
    private static final int MAX_COMPLETION_TOKENS = 1_200;

    private final AiChatClient aiChatClient;
    private final AiGenerationSupport support;

    public JdAnalysisService(AiChatClient aiChatClient, AiGenerationSupport support) {
        this.aiChatClient = aiChatClient;
        this.support = support;
    }

    public AiResponses.JdAnalysisResponse analyse(AiRequests.JdAnalysisRequest request) {
        PromptRegistry.Prompt prompt = support.resolvePrompt(PROMPT, request.promptVersion());

        String fenced = UntrustedContent.fence(
                "JOB_DESCRIPTION", request.jobDescriptionText(), MAX_JD_CHARS);

        AiGenerationSupport.ValidatedCompletion completion = support.completeAndValidate(
                aiChatClient, prompt.body(), fenced, PROMPT, SCHEMA, MAX_COMPLETION_TOKENS);

        return new AiResponses.JdAnalysisResponse(completion.content(),
                new AiResponses.Provenance(prompt.versionLabel(), completion.result().model(),
                        completion.result().totalTokens(), completion.repaired()));
    }
}
