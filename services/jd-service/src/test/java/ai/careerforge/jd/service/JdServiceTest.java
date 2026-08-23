package ai.careerforge.jd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.jd.client.AiClientDtos.JdAnalysisResponse;
import ai.careerforge.jd.client.AiServiceClient;
import ai.careerforge.jd.domain.JdAnalysis;
import ai.careerforge.jd.domain.JdVersion;
import ai.careerforge.jd.domain.JobDescription;
import ai.careerforge.jd.fetch.JdUrlFetcher;
import ai.careerforge.jd.fetch.JobPostingExtractor;
import ai.careerforge.jd.repository.JdAnalysisRepository;
import ai.careerforge.jd.repository.JdVersionRepository;
import ai.careerforge.jd.repository.JobDescriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JdService#analyse}'s cache-through behaviour — the half of ADR-038's call-count budget
 * that lives here, not in {@code JdOptimizationService}: a "warm" JD version (already analysed)
 * must cost zero Groq calls, and a "cold" one exactly one.
 */
class JdServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JD_ID = "jd-1";

    private JobDescriptionRepository jobDescriptions;
    private JdVersionRepository jdVersions;
    private JdAnalysisRepository jdAnalyses;
    private AiServiceClient aiServiceClient;
    private JdService service;

    private JobDescription jd;
    private JdVersion version;

    @BeforeEach
    void setUp() {
        jobDescriptions = mock(JobDescriptionRepository.class);
        jdVersions = mock(JdVersionRepository.class);
        jdAnalyses = mock(JdAnalysisRepository.class);
        aiServiceClient = mock(AiServiceClient.class);
        service = new JdService(jobDescriptions, jdVersions, jdAnalyses, aiServiceClient,
                new ObjectMapper(), mock(JdUrlFetcher.class), mock(JobPostingExtractor.class));

        jd = mock(JobDescription.class);
        when(jd.id()).thenReturn(JD_ID);
        when(jd.currentVersion()).thenReturn(1);
        when(jobDescriptions.findByIdAndUserId(JD_ID, USER_ID)).thenReturn(Optional.of(jd));

        version = mock(JdVersion.class);
        when(version.id()).thenReturn("jdv-1");
        when(version.rawText()).thenReturn("A perfectly ordinary job posting with enough length.");
        when(jdVersions.findByJobDescriptionIdAndVersion(JD_ID, 1)).thenReturn(Optional.of(version));
    }

    @Test
    @DisplayName("ADR-038: a warm (already-analysed) JD version costs zero Groq calls")
    void warmAnalysisMakesZeroGroqCalls() {
        JdAnalysis cached = new JdAnalysis(JD_ID, "jdv-1", "Backend Engineer", "Acme", "Senior",
                java.util.List.of("Java"), java.util.List.of(), "jd-analysis@v2", "openai/gpt-oss-120b");
        when(jdAnalyses.findByJdVersionId("jdv-1")).thenReturn(Optional.of(cached));

        JdAnalysis result = service.analyse(USER_ID, JD_ID);

        assertThat(result).isSameAs(cached);
        verify(aiServiceClient, never()).analyseJd(any());
    }

    @Test
    @DisplayName("ADR-038: a cold (never-analysed) JD version costs exactly one Groq call")
    void coldAnalysisMakesExactlyOneGroqCall() throws Exception {
        when(jdAnalyses.findByJdVersionId("jdv-1")).thenReturn(Optional.empty());
        ObjectMapper mapper = new ObjectMapper();
        when(aiServiceClient.analyseJd(any())).thenReturn(new JdAnalysisResponse(
                mapper.readTree("""
                        {"isJobPosting":true,"jobTitle":"Backend Engineer","company":"Acme",
                         "seniority":"Senior","requirements":[],"keywords":["Java"]}"""),
                mapper.readTree("{\"promptVersion\":\"jd-analysis@v2\",\"model\":\"openai/gpt-oss-120b\"}")));
        when(jdAnalyses.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.analyse(USER_ID, JD_ID);

        verify(aiServiceClient, times(1)).analyseJd(any());
    }
}
