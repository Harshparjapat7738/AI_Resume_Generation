package ai.careerforge.ai.grounding;

import ai.careerforge.ai.api.dto.EvidenceItem;
import ai.careerforge.ai.prompt.UntrustedContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The anti-fabrication gate.
 *
 * <p>Schema validation proves the model returned the right <em>shape</em>. This proves it
 * returned the right <em>facts</em>. Every generated statement is checked against the text
 * of the evidence items it cites, and anything it asserts that the evidence does not
 * support is a violation (blueprint §13, §18).
 *
 * <p>The checks are deliberately conservative in one direction: a false positive costs one
 * regeneration, while a false negative puts an invented claim into a document a real
 * person sends to a real employer under their name. When in doubt, this class rejects.
 *
 * <p>What it verifies:
 * <ol>
 *   <li>every cited evidence id exists in the inventory</li>
 *   <li>every factual statement cites at least one id</li>
 *   <li>every number appears in the cited evidence</li>
 *   <li>every proper noun — employer, technology, product — appears somewhere in the
 *       profile</li>
 *   <li>every year and month appears in the cited evidence</li>
 *   <li>no email address, phone number or URL appears at all</li>
 *   <li>no zero-width or bidirectional characters appear</li>
 * </ol>
 */
@Component
public class GroundingValidator {

    private static final Logger log = LoggerFactory.getLogger(GroundingValidator.class);

    /** Numbers that make a quantified claim: 40%, 3x, $2M, 12k, 1,500, 99.9. */
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?<![\\w-])(?:[$€£₹]\\s?)?\\d+(?:[.,]\\d+)*\\s?(?:%|x|k|m|bn|b|\\+)?(?![\\w-])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");

    private static final Pattern MONTH = Pattern.compile(
            "\\b(January|February|March|April|May|June|July|August|September|October|"
                    + "November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern URL = Pattern.compile(
            "\\b(?:https?://|www\\.)\\S+|\\b[\\w.-]+\\.(?:com|net|org|io|dev|ai|co|uk|in)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL =
            Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");

    private static final Pattern PHONE =
            Pattern.compile("(?<![\\w-])(?:\\+\\d{1,3}[\\s-]?)?(?:\\(\\d{2,4}\\)[\\s-]?)?"
                    + "\\d{3,4}[\\s-]?\\d{3,4}[\\s-]?\\d{0,4}(?![\\w-])");

    /**
     * Capitalised tokens, including dotted and hyphenated forms so that {@code Node.js},
     * {@code CI/CD} and {@code Spring-Boot} are caught as single entities.
     */
    private static final Pattern PROPER_NOUN =
            Pattern.compile("\\b[A-Z][A-Za-z0-9]*(?:[.+#/-][A-Za-z0-9]+)*\\b");

    /**
     * Capitalised words that carry no factual claim — sentence starters, months, and the
     * vocabulary of ordinary resume prose. Without this list, every sentence's first word
     * would be reported as an unsupported entity.
     */
    private static final Set<String> COMMON_CAPITALISED = Set.of(
            "a", "an", "the", "and", "or", "but", "for", "with", "within", "across", "using",
            "led", "built", "designed", "developed", "delivered", "implemented", "improved",
            "reduced", "increased", "created", "managed", "owned", "drove", "shipped",
            "collaborated", "partnered", "migrated", "refactored", "automated", "optimised",
            "optimized", "launched", "maintained", "supported", "coordinated", "mentored",
            "analysed", "analyzed", "researched", "documented", "tested", "deployed",
            "engineer", "engineering", "developer", "software", "senior", "junior", "lead",
            "team", "teams", "project", "projects", "product", "platform", "system",
            "systems", "service", "services", "application", "applications", "experience",
            "responsible", "responsibilities", "worked", "working", "years", "year",
            "january", "february", "march", "april", "may", "june", "july", "august",
            "september", "october", "november", "december", "present", "current",
            "i", "my", "we", "our", "this", "that", "these", "those", "it", "its",
            "in", "on", "at", "to", "of", "by", "as", "from", "into", "over", "through");

    /**
     * Validates generated statements.
     *
     * @param statements the generated content, each with its location and cited ids
     * @param inventory  the complete evidence inventory supplied to the model
     * @return a report; {@code passed} is true only when nothing at all was found
     */
    public GroundingReport validate(List<GeneratedStatement> statements,
                                    Collection<EvidenceItem> inventory) {

        Map<String, EvidenceItem> byId = inventory.stream().collect(
                Collectors.toMap(EvidenceItem::evidenceId, Function.identity(), (a, b) -> a));

        // Proper nouns are checked against the WHOLE profile, not just the cited items:
        // naming a technology the candidate genuinely lists elsewhere is a relevance
        // mistake, not a fabrication, and should not block the generation.
        Set<String> profileVocabulary = buildVocabulary(inventory);

        List<GroundingViolation> violations = new ArrayList<>();

        for (GeneratedStatement statement : statements) {
            String text = statement.text();
            String location = statement.location();

            if (text == null || text.isBlank()) {
                continue;
            }

            if (UntrustedContent.containsInvisibleCharacters(text)) {
                violations.add(new GroundingViolation(GroundingViolation.Rule.HIDDEN_CHARACTERS,
                        location, "zero-width or bidirectional character present", null));
            }

            find(URL, text).forEach(match -> violations.add(new GroundingViolation(
                    GroundingViolation.Rule.EXTERNAL_URL, location, match, null)));
            find(EMAIL, text).forEach(match -> violations.add(new GroundingViolation(
                    GroundingViolation.Rule.UNSUPPORTED_CONTACT, location,
                    "email address in generated text", null)));

            if (statement.evidenceIds().isEmpty()) {
                violations.add(new GroundingViolation(GroundingViolation.Rule.MISSING_EVIDENCE_ID,
                        location, "statement cites no evidence", null));
                continue;
            }

            List<EvidenceItem> cited = new ArrayList<>();
            for (String evidenceId : statement.evidenceIds()) {
                EvidenceItem item = byId.get(evidenceId);
                if (item == null) {
                    violations.add(new GroundingViolation(
                            GroundingViolation.Rule.UNKNOWN_EVIDENCE_ID, location,
                            "cited id does not exist", evidenceId));
                } else {
                    cited.add(item);
                }
            }
            if (cited.isEmpty()) {
                continue;
            }

            String citedText = normalise(cited.stream()
                    .map(EvidenceItem::searchableText)
                    .collect(Collectors.joining(" ")));

            // A phone-shaped match is only meaningful when the text is not simply a date
            // or a metric that already appears in the evidence.
            find(PHONE, text).stream()
                    .filter(match -> digitsOnly(match).length() >= 7)
                    .filter(match -> !citedText.contains(normalise(match)))
                    .forEach(match -> violations.add(new GroundingViolation(
                            GroundingViolation.Rule.UNSUPPORTED_CONTACT, location,
                            "phone-like number in generated text", null)));

            for (String number : find(NUMERIC_CLAIM, text)) {
                if (!supportsNumber(citedText, number)) {
                    violations.add(new GroundingViolation(
                            GroundingViolation.Rule.INVENTED_METRIC, location,
                            number.strip(), statement.evidenceIds().get(0)));
                }
            }

            for (String year : find(YEAR, text)) {
                if (!citedText.contains(year)) {
                    violations.add(new GroundingViolation(
                            GroundingViolation.Rule.UNSUPPORTED_DATE, location, year,
                            statement.evidenceIds().get(0)));
                }
            }

            for (String month : find(MONTH, text)) {
                if (!citedText.contains(normalise(month))) {
                    violations.add(new GroundingViolation(
                            GroundingViolation.Rule.UNSUPPORTED_DATE, location, month,
                            statement.evidenceIds().get(0)));
                }
            }

            for (String noun : find(PROPER_NOUN, text)) {
                String normalised = normalise(noun);
                if (COMMON_CAPITALISED.contains(normalised) || normalised.length() < 2) {
                    continue;
                }
                if (!profileVocabulary.contains(normalised)) {
                    violations.add(new GroundingViolation(
                            GroundingViolation.Rule.UNSUPPORTED_ENTITY, location, noun,
                            statement.evidenceIds().get(0)));
                }
            }
        }

        GroundingReport report = GroundingReport.of(violations, statements.size());
        log.info("Grounding: {}", report.summary());
        return report;
    }

    /**
     * A number is supported when the same digits appear in the cited evidence. Formatting
     * is ignored — evidence saying {@code "1500 users"} supports {@code "1,500 users"} —
     * but the digits themselves must be present. This is what stops "improved performance"
     * becoming "improved performance by 40%".
     */
    private boolean supportsNumber(String citedText, String number) {
        String digits = digitsOnly(number);
        if (digits.isEmpty()) {
            return true;
        }
        return digitsOnly(citedText).contains(digits) || citedText.contains(normalise(number));
    }

    private Set<String> buildVocabulary(Collection<EvidenceItem> inventory) {
        Set<String> vocabulary = new HashSet<>();
        for (EvidenceItem item : inventory) {
            for (String token : normalise(item.searchableText()).split("[^a-z0-9.+#/-]+")) {
                if (!token.isBlank()) {
                    vocabulary.add(token);
                    // Index sub-tokens too, so evidence containing "spring-boot" also
                    // supports the words "Spring" and "Boot" written separately.
                    for (String part : token.split("[.+#/-]")) {
                        if (part.length() > 1) {
                            vocabulary.add(part);
                        }
                    }
                }
            }
        }
        return vocabulary;
    }

    private List<String> find(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    private String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    /**
     * One generated statement awaiting verification.
     *
     * @param location    stable path into the output, used to drop exactly this statement
     *                    if regeneration also fails
     * @param text        the generated text
     * @param evidenceIds the ids the model cited as support
     */
    public record GeneratedStatement(String location, String text, List<String> evidenceIds) {

        public GeneratedStatement {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }
}
