package ai.careerforge.ai.client;

/**
 * Which provider actually served (or attempted) a completion. Purely descriptive/telemetry —
 * business services never branch on it (ADR-039); only {@link AiProviderRouter} does.
 */
public enum AiProvider {
    GROQ,
    GEMINI
}
