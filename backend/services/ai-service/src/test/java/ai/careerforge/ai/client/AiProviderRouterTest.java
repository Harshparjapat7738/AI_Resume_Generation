package ai.careerforge.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.careerforge.ai.config.AiFallbackProperties;
import ai.careerforge.ai.config.GeminiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AiProviderRouter} — the one place Groq-vs-Gemini is decided (ADR-039). Every test here
 * uses mocked {@link GroqClient}/{@link GeminiClient}, since this class's only job is the
 * routing decision itself, never the wire protocol either provider speaks (that's
 * {@code GroqClientTest}/{@code GeminiClientTest}).
 */
class AiProviderRouterTest {

    private static final String OPERATION = "jd-analysis";

    private GroqClient groqClient;
    private GeminiClient geminiClient;
    private GeminiProperties usableGemini;
    private AiFallbackProperties fallbackEnabled;
    private GroqCooldown cooldown;
    private AiProviderRouter router;

    private final AiChatClient.AiChatResult groqResult =
            new AiChatClient.AiChatResult("{\"ok\":true}", "openai/gpt-oss-120b", 10, AiProvider.GROQ);
    private final AiChatClient.AiChatResult geminiResult =
            new AiChatClient.AiChatResult("{\"ok\":true}", "gemini-2.5-flash", 8, AiProvider.GEMINI);

    @BeforeEach
    void setUp() {
        groqClient = mock(GroqClient.class);
        geminiClient = mock(GeminiClient.class);
        usableGemini = new GeminiProperties(true, "test-key", "https://example.invalid",
                "gemini-2.5-flash", 60, 1200);
        fallbackEnabled = new AiFallbackProperties(java.util.Set.of("jd-analysis", "jd-optimization"));
        cooldown = mock(GroqCooldown.class);
        when(cooldown.isInCooldown(anyString())).thenReturn(false);
        router = new AiProviderRouter(groqClient, geminiClient, usableGemini, fallbackEnabled, cooldown,
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("1. Groq success: Groq called once, Gemini not called")
    void groqSuccessNeverCallsGemini() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(groqResult);

        AiChatClient.AiChatResult result = router.complete("sys", "user", OPERATION, 1200);

        assertThat(result.provider()).isEqualTo(AiProvider.GROQ);
        verify(groqClient, times(1)).complete(anyString(), anyString(), eq(OPERATION), any());
        verify(geminiClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("2. Groq 429 (temporary): Gemini called once, Gemini result returned")
    void groq429FallsBackToGemini() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq rate limit", true, true, false, 2L, null));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        AiChatClient.AiChatResult result = router.complete("sys", "user", OPERATION, 1200);

        assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
        verify(groqClient, times(1)).complete(any(), any(), any(), any());
        verify(geminiClient, times(1)).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("3. Groq timeout (retryable, not rate-limited): Gemini fallback occurs")
    void groqTimeoutFallsBackToGemini() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq timed out", true));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        AiChatClient.AiChatResult result = router.complete("sys", "user", OPERATION, 1200);

        assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("4. Groq 5xx exhausted (retryable): Gemini fallback occurs once GroqClient's own retries are done")
    void groq5xxAfterExhaustedRetriesFallsBackToGemini() {
        // GroqClient's own retry policy already ran and gave up by the time this exception
        // reaches the router — the router never re-implements that retry loop, it only reacts
        // to the final, already-exhausted failure.
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq unavailable after 2 retries", true));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        router.complete("sys", "user", OPERATION, 1200);

        verify(groqClient, times(1)).complete(any(), any(), any(), any());
        verify(geminiClient, times(1)).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("5. Groq invalid request (deterministic, non-retryable): Gemini NOT called")
    void groqDeterministicFailureNeverFallsBack() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq rejected the request: 400", false));

        assertThatThrownBy(() -> router.complete("sys", "user", OPERATION, 1200))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException e = (AiProviderException) ex;
                    assertThat(e.provider()).isEqualTo(AiProvider.GROQ);
                    assertThat(e.failureType()).isEqualTo(AiFailureType.INVALID_REQUEST);
                });
        verify(geminiClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("8. Both providers fail: one controlled AiProviderException, tagged with the final provider")
    void bothProvidersFailingReturnsControlledError() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq rate limit", true, true, false, null, null));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GeminiException("Gemini unavailable", true));

        assertThatThrownBy(() -> router.complete("sys", "user", OPERATION, 1200))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException e = (AiProviderException) ex;
                    assertThat(e.provider()).isEqualTo(AiProvider.GEMINI);
                });
    }

    @Test
    @DisplayName("Fallback never happens for an operation not in the allow-list (e.g. email-content)")
    void unlistedOperationNeverFallsBack() {
        when(groqClient.complete(anyString(), anyString(), eq("email-content"), any()))
                .thenThrow(new GroqException("Groq rate limit", true, true, false, null, null));

        assertThatThrownBy(() -> router.complete("sys", "user", "email-content", null))
                .isInstanceOf(AiProviderException.class);
        verify(geminiClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fallback never happens when Gemini isn't configured, even for an allow-listed operation")
    void geminiNotConfiguredNeverFallsBack() {
        GeminiProperties disabledGemini = new GeminiProperties(false, "", null, "gemini-2.5-flash", 60, 1200);
        AiProviderRouter routerWithoutGemini = new AiProviderRouter(groqClient, geminiClient, disabledGemini,
                fallbackEnabled, cooldown, new SimpleMeterRegistry());
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq rate limit", true, true, false, null, null));

        assertThatThrownBy(() -> routerWithoutGemini.complete("sys", "user", OPERATION, 1200))
                .isInstanceOf(AiProviderException.class);
        verify(geminiClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A 'too large' 429 within our own configured ceiling is still fallback-eligible")
    void tooLargeWithinOurCeilingFallsBack() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("too large", false, true, true, null, null));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        String smallPayload = "short content well within the ceiling";
        AiChatClient.AiChatResult result = router.complete("sys", smallPayload, OPERATION, 1200);

        assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("A 'too large' 429 where our OWN payload exceeds our configured ceiling is an internal bug, never falls back")
    void tooLargeExceedingOurOwnCeilingNeverFallsBack() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("too large", false, true, true, null, null));
        String oversized = "x".repeat(50_000); // exceeds the 43,000-char jd-analysis ceiling

        assertThatThrownBy(() -> router.complete("sys", oversized, OPERATION, 1200))
                .isInstanceOf(AiProviderException.class);
        verify(geminiClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("9/15. Groq cooldown active: goes straight to Gemini, Groq is never called")
    void activeCooldownSkipsGroqEntirely() {
        when(cooldown.isInCooldown(OPERATION)).thenReturn(true);
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        AiChatClient.AiChatResult result = router.complete("sys", "user", OPERATION, 1200);

        assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
        verify(groqClient, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A rate-limited Groq failure marks the operation's cooldown")
    void rateLimitedFailureMarksCooldown() {
        when(groqClient.complete(anyString(), anyString(), eq(OPERATION), any()))
                .thenThrow(new GroqException("Groq rate limit", true, true, false, null, null));
        when(geminiClient.complete(anyString(), anyString(), eq(OPERATION), any())).thenReturn(geminiResult);

        router.complete("sys", "user", OPERATION, 1200);

        verify(cooldown).markUnhealthy(eq(OPERATION), any());
    }
}
