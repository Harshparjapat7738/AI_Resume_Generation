package ai.careerforge.jd.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.careerforge.jd.fetch.JobPostingExtractor.ExtractedJd;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the JSON-LD/text extraction logic in isolation from the network — real job
 * board HTML varies too much (and is unpredictable to fetch in CI) to be a reliable test
 * fixture, so these hand-craft representative HTML instead.
 */
class JobPostingExtractorTest {

    private final JobPostingExtractor extractor = new JobPostingExtractor(new ObjectMapper());

    @Test
    void extractsStructuredFieldsFromJobPostingJsonLd() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org/",
                  "@type": "JobPosting",
                  "title": "Senior Backend Engineer",
                  "hiringOrganization": { "@type": "Organization", "name": "Acme Cloud" },
                  "jobLocation": { "@type": "Place", "address": {
                      "addressLocality": "Bengaluru", "addressRegion": "KA", "addressCountry": "IN" } },
                  "skills": "Java, Kubernetes, AWS",
                  "experienceRequirements": { "@type": "OccupationalExperienceRequirements", "monthsOfExperience": 60 },
                  "description": "<p>We are looking for a backend engineer.</p><ul><li>Build APIs</li><li>Own services</li></ul>"
                }
                </script>
                </head><body><nav>Home | About</nav><main>ignored</main></body></html>
                """;

        ExtractedJd result = extractor.extract(html);

        assertEquals("Senior Backend Engineer", result.title());
        assertEquals("Acme Cloud", result.company());
        assertEquals("Bengaluru, KA, IN", result.location());
        assertEquals("Java, Kubernetes, AWS", result.skillsSummary());
        assertEquals("5 year(s) of experience", result.experienceSummary());
        assertEquals("URL_JSON_LD", result.extractionMethod());
        assertTrue(result.rawText().contains("backend engineer"));
        assertTrue(result.rawText().contains("Build APIs"));
        // The description came from JSON-LD, not the page body — nav text must not leak in.
        assertTrue(!result.rawText().contains("About"));
    }

    @Test
    void findsJobPostingInsideAtGraphArray() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                { "@context": "https://schema.org/", "@graph": [
                    { "@type": "WebPage", "name": "Careers" },
                    { "@type": ["JobPosting"], "title": "Data Analyst",
                      "hiringOrganization": "Northwind", "description": "Analyse data pipelines." }
                  ] }
                </script>
                </head><body></body></html>
                """;

        ExtractedJd result = extractor.extract(html);

        assertEquals("Data Analyst", result.title());
        assertEquals("Northwind", result.company());
    }

    @Test
    void fallsBackToVisibleTextWhenNoJobPostingJsonLdIsPresent() {
        String html = """
                <html><head><script>var x = 1;</script></head>
                <body>
                  <nav>Site nav</nav>
                  <main>
                    <h1>Backend Engineer</h1>
                    <p>We need someone who knows Java and Spring Boot well.</p>
                    <ul><li>Own the payments service</li><li>Mentor juniors</li></ul>
                  </main>
                  <footer>Copyright 2026</footer>
                </body></html>
                """;

        ExtractedJd result = extractor.extract(html);

        assertNull(result.title());
        assertNull(result.company());
        assertEquals("URL_TEXT", result.extractionMethod());
        assertTrue(result.rawText().contains("Backend Engineer"));
        assertTrue(result.rawText().contains("Own the payments service"));
        // script/nav/footer are stripped from the fallback text extraction.
        assertTrue(!result.rawText().contains("var x = 1"));
        assertTrue(!result.rawText().contains("Site nav"));
        assertTrue(!result.rawText().contains("Copyright"));
    }

    @Test
    void neverLeaksTheInternalBreakMarkerIntoVisibleText() {
        // Regression test: a real page (a nested div with no <p>/<li> at all, matching what
        // example.com actually serves) revealed jsoup's whitespace collapsing could leave
        // "JDBREAKMARKER" behind verbatim in the text shown to users.
        String html = """
                <html><body>
                  <div>
                    <h1>Example Domain</h1>
                    <div>This domain is for use in documentation examples.<br>Second line.</div>
                  </div>
                </body></html>
                """;

        ExtractedJd result = extractor.extract(html);

        assertTrue(!result.rawText().toUpperCase().contains("JDBREAKMARKER"),
                "leaked marker in: " + result.rawText());
        assertTrue(result.rawText().contains("Example Domain"));
        assertTrue(result.rawText().contains("Second line"));
    }

    @Test
    void malformedJsonLdIsSkippedRatherThanFailingTheWholeExtraction() {
        String html = """
                <html><head>
                <script type="application/ld+json">{ not valid json </script>
                </head><body><p>A perfectly normal job description page.</p></body></html>
                """;

        ExtractedJd result = extractor.extract(html);

        assertEquals("URL_TEXT", result.extractionMethod());
        assertTrue(result.rawText().contains("perfectly normal job description"));
    }
}
