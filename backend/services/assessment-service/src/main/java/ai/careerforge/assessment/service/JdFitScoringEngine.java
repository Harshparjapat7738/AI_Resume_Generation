package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.ExperienceDto;
import ai.careerforge.assessment.client.ClientDtos.JdAnalysisDto;
import ai.careerforge.assessment.client.ClientDtos.RequirementDto;
import ai.careerforge.assessment.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.assessment.client.ClientDtos.OptimizationDataDto;
import ai.careerforge.assessment.client.ClientDtos.OptimizationMatchDto;
import ai.careerforge.assessment.domain.RecommendationItem;
import ai.careerforge.assessment.domain.RequirementMatchResult;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic JD-compatibility scoring: {@code 0.50*coverage + 0.20*keywordMatch +
 * 0.20*seniorityMatch + 0.10*recency} (docs/CODEBASE.md &sect;2, assessment-service). Every
 * sub-score is computed from real data already produced upstream — resume-service's
 * evidence matches, jd-service's requirements/keywords, and the candidate's own experience
 * dates. Nothing here is asked of the LLM or invented.
 */
@Component
public class JdFitScoringEngine {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public record Result(
            double compatibilityScore, double coverage, double keywordMatch, double seniorityMatch,
            double recency, List<RequirementMatchResult> requirementMatches,
            List<RequirementMatchResult> unmetHardRequirements, List<String> matchedKeywords,
            List<String> missingKeywords, String readinessBand, String bandRule,
            List<RecommendationItem> recommendations) {
    }

    /**
     * Scores a JD optimization, not a generated resume (ADR-033).
     *
     * <p>The two inputs that changed and why:
     * <ul>
     *   <li><strong>Requirement coverage</strong> now reads the optimization's own
     *       {@code requirementMatches} instead of a resume version's {@code evidenceMatches}.
     *       Same shape, same verdicts, same weighting — only the source moved.</li>
     *   <li><strong>Keyword match</strong> now asks whether the candidate's profile actually
     *       backs each JD term (does the optimization map it to evidence?), rather than
     *       substring-searching generated resume prose. That is both the only option left and
     *       the more honest measure: the old check could be satisfied by the model happening to
     *       use a word, which said nothing about whether the candidate could support it.</li>
     * </ul>
     * Seniority and recency are unchanged — they always read the profile's experiences, never
     * the resume.
     */
    public Result score(JdOptimizationDto optimization, JdAnalysisDto jd, List<ExperienceDto> experiences) {
        var data = optimization.optimisation();
        List<RequirementDto> requirements = jd.requirements() == null ? List.of() : jd.requirements();
        List<RequirementMatchResult> matches = buildRequirementMatches(requirements, data);

        double coverage = ratioOf(matches, m -> !"NONE".equals(m.matchStrength()));
        List<RequirementMatchResult> unmetHard = matches.stream()
                .filter(m -> "HARD_REQUIRED".equals(m.type()) && "NONE".equals(m.matchStrength()))
                .toList();

        // A keyword counts as matched when the optimization mapped it to real evidence.
        Set<String> backed = new java.util.HashSet<>();
        if (data != null && data.keywords() != null) {
            for (var keyword : data.keywords()) {
                if (keyword.term() != null && keyword.evidenceIds() != null && !keyword.evidenceIds().isEmpty()) {
                    backed.add(keyword.term().toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> keywords = jd.keywords() == null ? List.of() : jd.keywords();
        List<String> matchedKeywords = keywords.stream()
                .filter(k -> k != null && !k.isBlank() && backed.contains(k.toLowerCase(Locale.ROOT)))
                .toList();
        List<String> missingKeywords = keywords.stream()
                .filter(k -> !matchedKeywords.contains(k))
                .toList();
        double keywordMatch = keywords.isEmpty() ? 1.0 : (double) matchedKeywords.size() / keywords.size();

        double seniorityMatch = seniorityMatch(jd.seniority(), experiences);
        double recency = recency(experiences);

        double compatibilityScore = 0.50 * coverage + 0.20 * keywordMatch + 0.20 * seniorityMatch + 0.10 * recency;

        String[] band = readinessBand(compatibilityScore, unmetHard.isEmpty());
        List<RecommendationItem> recommendations = buildRecommendations(unmetHard, missingKeywords, seniorityMatch);

        return new Result(compatibilityScore, coverage, keywordMatch, seniorityMatch, recency, matches,
                unmetHard, matchedKeywords, missingKeywords, band[0], band[1], recommendations);
    }

    /** Joins the JD's requirement text onto the optimization's verdicts. A requirement the
     *  optimization never reported on scores NONE — absence of a verdict is not a match. */
    private List<RequirementMatchResult> buildRequirementMatches(
            List<RequirementDto> requirements, OptimizationDataDto data) {
        Map<String, OptimizationMatchDto> byRequirement = new java.util.HashMap<>();
        if (data != null && data.requirementMatches() != null) {
            for (OptimizationMatchDto match : data.requirementMatches()) {
                if (match.requirementId() != null) {
                    byRequirement.put(match.requirementId(), match);
                }
            }
        }
        List<RequirementMatchResult> results = new ArrayList<>();
        for (RequirementDto req : requirements) {
            OptimizationMatchDto match = byRequirement.get(req.requirementId());
            String strength = match != null && match.matchStrength() != null ? match.matchStrength() : "NONE";
            List<String> evidenceIds = match != null && match.evidenceIds() != null
                    ? match.evidenceIds() : List.of();
            results.add(new RequirementMatchResult(req.requirementId(), req.text(), req.type(), strength, evidenceIds));
        }
        return results;
    }

    private double ratioOf(List<RequirementMatchResult> matches, java.util.function.Predicate<RequirementMatchResult> pred) {
        if (matches.isEmpty()) return 0.0;
        long count = matches.stream().filter(pred).count();
        return (double) count / matches.size();
    }

    private double seniorityMatch(String jdSeniority, List<ExperienceDto> experiences) {
        Integer expectedMinYears = expectedYearsFor(jdSeniority);
        if (expectedMinYears == null) {
            return 0.6; // not confidently assessable from the JD's seniority label
        }
        double candidateYears = totalYearsOfExperience(experiences);
        if (expectedMinYears == 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, candidateYears / expectedMinYears));
    }

    private Integer expectedYearsFor(String seniority) {
        if (seniority == null || seniority.isBlank()) return null;
        String s = seniority.toLowerCase(Locale.ROOT);
        if (s.contains("junior") || s.contains("entry") || s.contains("intern")) return 0;
        if (s.contains("mid") || s.contains("intermediate")) return 2;
        if (s.contains("senior")) return 5;
        if (s.contains("lead") || s.contains("principal") || s.contains("staff")) return 8;
        return null;
    }

    private double totalYearsOfExperience(List<ExperienceDto> experiences) {
        if (experiences == null || experiences.isEmpty()) return 0.0;
        double totalMonths = 0;
        for (ExperienceDto exp : experiences) {
            YearMonth start = parseYearMonth(exp.start());
            if (start == null) continue;
            YearMonth end = exp.current() ? YearMonth.now() : parseYearMonth(exp.end());
            if (end == null || end.isBefore(start)) continue;
            totalMonths += Period.between(start.atDay(1), end.atDay(1)).toTotalMonths();
        }
        return totalMonths / 12.0;
    }

    private double recency(List<ExperienceDto> experiences) {
        if (experiences == null || experiences.isEmpty()) return 0.0;
        boolean anyCurrent = experiences.stream().anyMatch(ExperienceDto::current);
        if (anyCurrent) return 1.0;

        YearMonth mostRecentEnd = null;
        for (ExperienceDto exp : experiences) {
            YearMonth end = parseYearMonth(exp.end());
            if (end != null && (mostRecentEnd == null || end.isAfter(mostRecentEnd))) {
                mostRecentEnd = end;
            }
        }
        if (mostRecentEnd == null) return 0.0;

        long monthsAgo = Period.between(mostRecentEnd.atDay(1), YearMonth.now().atDay(1)).toTotalMonths();
        if (monthsAgo <= 12) return 1.0;
        if (monthsAgo <= 24) return 0.7;
        if (monthsAgo <= 48) return 0.4;
        return 0.2;
    }

    private YearMonth parseYearMonth(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return YearMonth.parse(value.trim(), YEAR_MONTH);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** @return {band, rule} */
    private String[] readinessBand(double compatibilityScore, boolean noUnmetHardRequirements) {
        if (compatibilityScore >= 0.85 && noUnmetHardRequirements) {
            return new String[] {"STRONG", "compatibilityScore >= 0.85 and every hard requirement is covered"};
        }
        if (compatibilityScore >= 0.65) {
            return new String[] {"COMPETITIVE", "compatibilityScore >= 0.65"};
        }
        if (compatibilityScore >= 0.40) {
            return new String[] {"STRETCH", "compatibilityScore >= 0.40"};
        }
        return new String[] {"WEAK_FIT", "compatibilityScore < 0.40"};
    }

    private List<RecommendationItem> buildRecommendations(
            List<RequirementMatchResult> unmetHard, List<String> missingKeywords, double seniorityMatch) {
        List<RecommendationItem> items = new ArrayList<>();
        for (RequirementMatchResult req : unmetHard) {
            items.add(new RecommendationItem("GAP", "HIGH",
                    "No evidence in your profile supports: \"" + req.text() + "\". Reported as a gap — add it to "
                            + "your profile only if it's genuinely true.",
                    req.requirementId()));
        }
        if (!missingKeywords.isEmpty()) {
            Set<String> top = new LinkedHashSet<>(missingKeywords.subList(0, Math.min(5, missingKeywords.size())));
            items.add(new RecommendationItem("KEYWORD", "MEDIUM",
                    "Consider highlighting genuine experience with: " + String.join(", ", top)
                            + " — only if you actually have it.",
                    null));
        }
        if (seniorityMatch < 0.5) {
            items.add(new RecommendationItem("SENIORITY", "MEDIUM",
                    "This role appears to expect more years of experience than your profile currently reflects.",
                    null));
        }
        return items;
    }
}
