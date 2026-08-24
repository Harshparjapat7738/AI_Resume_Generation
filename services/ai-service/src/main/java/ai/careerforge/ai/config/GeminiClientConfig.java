package ai.careerforge.ai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * The single {@link WebClient} used to reach Gemini (ADR-039) — a distinct bean from
 * {@code groqWebClient} since the two providers have different base URLs and auth schemes
 * (Gemini's key goes in the {@code x-goog-api-key} header, not {@code Authorization: Bearer}).
 * Built even when Gemini is disabled/unconfigured — {@link GeminiProperties#isUsable()} is what
 * gates whether {@link ai.careerforge.ai.client.AiProviderRouter} ever calls
 * {@link ai.careerforge.ai.client.GeminiClient}, not whether this bean exists — so a missing key
 * fails at first *use*, with a clear message, not at context startup.
 */
@Configuration
public class GeminiClientConfig {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024; // 2 MB

    @Bean
    public WebClient geminiWebClient(GeminiProperties properties) {
        int timeoutSeconds = properties.timeoutSeconds() > 0 ? properties.timeoutSeconds() : 60;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds)));

        String baseUrl = (properties.baseUrl() == null || properties.baseUrl().isBlank())
                ? "https://generativelanguage.googleapis.com" : properties.baseUrl();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES));
        // The API key header is added per-request in GeminiClient, not here: this bean is built
        // unconditionally at startup (see class Javadoc), and properties.apiKey() may be blank
        // in a deployment that never uses Gemini — a blank default header would be silently
        // wrong rather than loudly absent.
        return builder.build();
    }
}
