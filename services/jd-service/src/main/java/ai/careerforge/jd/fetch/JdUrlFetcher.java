package ai.careerforge.jd.fetch;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.jd.security.SsrfGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches a job description page over HTTP(S), with every hop — including redirects —
 * validated by {@link SsrfGuard} first. A public URL redirecting to a private one is
 * exactly the SSRF pattern the guard exists to stop, so redirects are followed manually
 * (never automatically) with the same validation applied to each new target.
 */
@Component
public class JdUrlFetcher {

    private static final Logger log = LoggerFactory.getLogger(JdUrlFetcher.class);

    /** Generous enough for a real job posting page, small enough to bound memory use. */
    private static final long MAX_BODY_BYTES = 3 * 1024 * 1024; // 3 MB

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record FetchedPage(URI finalUrl, String html) {
    }

    public FetchedPage fetch(String rawUrl) {
        URI target = SsrfGuard.validate(rawUrl);

        for (int hop = 0; hop <= SsrfGuard.maxRedirects(); hop++) {
            HttpResponse<InputStream> response = send(target);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                URI redirectSource = target;
                URI redirectTarget = response.headers().firstValue("Location")
                        .map(location -> resolveRedirect(redirectSource, location))
                        .orElseThrow(() -> new ApiException(ErrorCode.JD_URL_BLOCKED,
                                "The page redirected without a destination."));
                closeQuietly(response);
                // Re-validate the redirect's destination through the full SSRF guard —
                // this is the whole point: it may not be the same host, or safe at all.
                target = SsrfGuard.validate(redirectTarget.toString());
                continue;
            }

            if (status != 200) {
                closeQuietly(response);
                throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                        "Unable to extract this job description from this URL.");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                closeQuietly(response);
                throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                        "That URL didn't return a web page CareerForge AI can read.");
            }

            String html = readBounded(response.body(), charsetOf(contentType));
            return new FetchedPage(target, html);
        }

        throw new ApiException(ErrorCode.JD_URL_BLOCKED, "Too many redirects.");
    }

    private HttpResponse<InputStream> send(URI target) {
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "CareerForgeAI-JobDescriptionFetcher/1.0 (+https://careerforge.ai; fetched on behalf of an authenticated user)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        try {
            return httpClient.send(request, BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            log.warn("JD URL fetch failed for host={}: {}", target.getHost(), ex.getMessage());
            throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                    "Unable to extract this job description from this URL.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                    "Unable to extract this job description from this URL.");
        }
    }

    private URI resolveRedirect(URI current, String location) {
        try {
            return current.resolve(location);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.JD_URL_BLOCKED, "The page redirected to an invalid URL.");
        }
    }

    private String readBounded(InputStream body, Charset charset) {
        try (InputStream in = body) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
            byte[] chunk = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                            "That page is too large to extract a job description from.");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(charset);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.JD_VALIDATION_ERROR,
                    "Unable to extract this job description from this URL.");
        }
    }

    private Charset charsetOf(String contentType) {
        int index = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (index < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = contentType.substring(index + "charset=".length()).trim();
        int semicolon = name.indexOf(';');
        if (semicolon >= 0) {
            name = name.substring(0, semicolon).trim();
        }
        try {
            return Charset.forName(name);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private void closeQuietly(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
