package ai.careerforge.jd.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts a job posting from fetched HTML two ways, in order:
 *
 * <ol>
 *   <li><strong>schema.org {@code JobPosting} JSON-LD</strong> — a standard many job boards
 *       and ATS platforms (Greenhouse, Lever, Workday, and others) embed. When present, this
 *       gives genuinely structured title/company/location/skills/experience, not a guess.
 *   <li><strong>Generic visible-text fallback</strong> — when no structured data is present,
 *       the page's readable text is extracted (scripts/styles/nav/footer stripped) as a raw
 *       block, exactly like a pasted job description. No structured fields are fabricated in
 *       this path — {@link ExtractedJd#title()} etc. are {@code null} rather than guessed.
 * </ol>
 *
 * <p>Not every site is supported the same way: pages without JobPosting JSON-LD only ever
 * produce the text fallback, and pages that block automated fetches entirely (many do) fail
 * before reaching this class at all (see {@link JdUrlFetcher}). Neither case is treated as
 * an error here — degrading gracefully is the point.
 */
@Component
public class JobPostingExtractor {

    /**
     * A sentinel jsoup's whitespace-collapsing {@code .text()} carries through intact. Can't
     * be a plain space (every other space in the document would be flattened away too) or a
     * short common substring like "BR" (would corrupt real words: "li-BR-ary", "a-BR-oad").
     */
    private static final String BREAK_MARKER = " JDBREAKMARKER ";

    private final ObjectMapper objectMapper;

    public JobPostingExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ExtractedJd(
            String title, String company, String location,
            String skillsSummary, String experienceSummary,
            String rawText, String extractionMethod) {
    }

    public ExtractedJd extract(String html) {
        Document document = Jsoup.parse(html);

        JsonNode jobPosting = findJobPostingJsonLd(document);
        if (jobPosting != null) {
            return fromJsonLd(jobPosting, document);
        }
        return fromVisibleText(document);
    }

    // ---- JSON-LD path ---------------------------------------------------------

    private JsonNode findJobPostingJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = objectMapper.readTree(script.data());
            } catch (Exception ex) {
                continue; // malformed JSON-LD on the page — skip, don't fail the whole fetch
            }
            JsonNode found = findJobPostingNode(root);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode findJobPostingNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findJobPostingNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (isJobPostingType(node)) {
            return node;
        }
        if (node.has("@graph")) {
            return findJobPostingNode(node.get("@graph"));
        }
        return null;
    }

    private boolean isJobPostingType(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isTextual()) {
            return "JobPosting".equalsIgnoreCase(type.asText());
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isTextual() && "JobPosting".equalsIgnoreCase(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ExtractedJd fromJsonLd(JsonNode job, Document document) {
        String title = textOf(job.get("title"));
        String company = companyOf(job.get("hiringOrganization"));
        String location = locationOf(job.get("jobLocation"));
        String skills = textOf(job.get("skills"));
        String experience = experienceOf(job.get("experienceRequirements"));

        String descriptionHtml = textOf(job.get("description"));
        String rawText = descriptionHtml != null && !descriptionHtml.isBlank()
                ? readableTextFromHtmlFragment(descriptionHtml)
                : readableBodyText(document);

        return new ExtractedJd(title, company, location, skills, experience, rawText, "URL_JSON_LD");
    }

    private String companyOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return textOf(node.get("name"));
    }

    private String locationOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode place = node.isArray() && !node.isEmpty() ? node.get(0) : node;
        if (place.isTextual()) {
            return place.asText();
        }
        JsonNode address = place.get("address");
        if (address == null) {
            return null;
        }
        if (address.isTextual()) {
            return address.asText();
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, address, "addressLocality");
        addIfPresent(parts, address, "addressRegion");
        addIfPresent(parts, address, "addressCountry");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, JsonNode node, String field) {
        String value = textOf(node.get(field));
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private String experienceOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        JsonNode months = node.get("monthsOfExperience");
        if (months != null && months.isNumber()) {
            int m = months.asInt();
            return m >= 12
                    ? (m / 12) + (m % 12 == 0 ? " year(s) of experience" : "+ years of experience")
                    : m + " months of experience";
        }
        return null;
    }

    private String textOf(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    // ---- fallback: generic visible text ----------------------------------

    private ExtractedJd fromVisibleText(Document document) {
        String rawText = readableBodyText(document);
        return new ExtractedJd(null, null, null, null, null, rawText, "URL_TEXT");
    }

    /** Renders an HTML fragment (e.g. a JSON-LD {@code description} field) to readable text,
     *  preserving paragraph and list-item breaks that flat {@code .text()} would collapse. */
    private String readableTextFromHtmlFragment(String htmlFragment) {
        Document fragment = Jsoup.parseBodyFragment(htmlFragment);
        return toReadableText(fragment.body());
    }

    private String readableBodyText(Document document) {
        Document clone = document.clone();
        clone.select("script, style, nav, footer, header, noscript, svg, iframe").remove();
        Element body = clone.body();
        return body != null ? toReadableText(body) : "";
    }

    private String toReadableText(Element root) {
        Element working = root.clone();
        working.select("br").before(BREAK_MARKER);
        for (Element block : working.select("p, li, h1, h2, h3, h4, h5, h6, div, tr")) {
            block.appendText(BREAK_MARKER);
        }
        // jsoup's whitespace-collapsing .text() can change how many spaces end up
        // immediately around the marker (none at a block boundary, several elsewhere) — an
        // exact-string replace missed those cases and let "JDBREAKMARKER" leak into real
        // output. Matching the token with any amount of surrounding whitespace is robust to
        // however jsoup normalised it.
        String withMarkers = working.text().replaceAll("\\s*" + BREAK_MARKER.trim() + "\\s*", "\n");
        return withMarkers.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
