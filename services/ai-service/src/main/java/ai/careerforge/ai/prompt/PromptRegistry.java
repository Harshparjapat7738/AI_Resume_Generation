package ai.careerforge.ai.prompt;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Loads versioned system prompts from {@code classpath:prompts/<name>/v<N>.txt}.
 *
 * <p>Prompts are versioned files rather than string constants so that a prompt change is a
 * reviewable diff, and so the exact version used can be recorded on every generated
 * artifact — without it, a resume produced last month cannot be reproduced or explained
 * (blueprint §37).
 *
 * <p>Prompts are loaded once at startup. A missing or unreadable prompt fails the service
 * immediately rather than at the first user request.
 */
@Component
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);
    private static final Pattern VERSION_FILE = Pattern.compile("v(\\d+)\\.txt$");

    private final Map<String, String> prompts = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestVersions = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:prompts/*/v*.txt");

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                Matcher matcher = VERSION_FILE.matcher(filename);
                if (!matcher.find()) {
                    continue;
                }
                int version = Integer.parseInt(matcher.group(1));
                String name = promptNameOf(resource);
                String body = StreamUtils.copyToString(resource.getInputStream(),
                        StandardCharsets.UTF_8).strip();

                prompts.put(key(name, version), body);
                latestVersions.merge(name, version, Math::max);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load prompt templates", ex);
        }

        if (prompts.isEmpty()) {
            throw new IllegalStateException(
                    "No prompts found on the classpath under prompts/<name>/v<N>.txt");
        }
        log.info("Loaded {} prompt version(s): {}", prompts.size(), latestVersions);
    }

    /** @return the newest version of the named prompt */
    public Prompt latest(String name) {
        Integer version = latestVersions.get(name);
        if (version == null) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
        return get(name, version);
    }

    /** @return a specific version, so an old artifact can be reproduced exactly */
    public Prompt get(String name, int version) {
        String body = prompts.get(key(name, version));
        if (body == null) {
            throw new IllegalArgumentException("Unknown prompt version: " + name + " v" + version);
        }
        return new Prompt(name, version, body);
    }

    public List<String> names() {
        return latestVersions.keySet().stream().sorted().toList();
    }

    public Map<String, Integer> latestVersions() {
        return Map.copyOf(latestVersions);
    }

    private String promptNameOf(Resource resource) throws IOException {
        String path = resource.getURL().getPath();
        String[] segments = path.split("/");
        return segments[segments.length - 2];
    }

    private String key(String name, int version) {
        return name + "@v" + version;
    }

    /**
     * A resolved prompt.
     *
     * @param name    logical name, e.g. {@code jd-analysis}
     * @param version file version, recorded on every artifact it produces
     * @param body    the system-message text
     */
    public record Prompt(String name, int version, String body) {

        public String versionLabel() {
            return name + "@v" + version;
        }
    }
}
