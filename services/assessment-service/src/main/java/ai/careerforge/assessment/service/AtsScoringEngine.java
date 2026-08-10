package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.ExperienceDto;
import ai.careerforge.assessment.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.assessment.client.ClientDtos.ResumeVersionDto;
import ai.careerforge.assessment.domain.AtsCheckResult;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic, Java-computed, weighted checks against the generated resume's structured
 * content — never asked of the LLM.
 *
 * <p><strong>Scope deviation — see ARCHITECTURE_DECISIONS.md ADR-014.</strong> The
 * blueprint's ten checks (MACHINE_READABLE_TEXT, ATS_SAFE_LAYOUT, STANDARD_FONTS,
 * HEADER_FOOTER_SAFETY, FILENAME, …) assume a rendered PDF/DOCX to inspect.
 * document-service doesn't exist yet, so there is no rendered artifact to check. This
 * engine scores what's actually measurable from the JSON content and the evidence behind
 * it — still deterministic, still explainable per sub-check, just honestly scoped.
 */
@Component
public class AtsScoringEngine {

    public static final String ENGINE_VERSION = "content-v1";

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public List<AtsCheckResult> score(ResumeVersionDto resume, PersonalInformationDto personalInfo,
                                      List<ExperienceDto> experiences, List<String> jdKeywords) {
        List<AtsCheckResult> checks = new ArrayList<>();
        checks.add(contactInfoPresent(personalInfo));
        checks.add(summaryPresent(resume.content()));
        checks.add(experienceSectionPresent(resume.content()));
        checks.add(dateConsistency(experiences));
        checks.add(bulletLengthSuitability(resume.content()));
        checks.add(keywordPresence(resume.content(), jdKeywords));
        checks.add(groundingIntegrity(resume.grounding()));
        return checks;
    }

    private AtsCheckResult contactInfoPresent(PersonalInformationDto info) {
        boolean hasName = info != null && isNotBlank(info.fullName());
        boolean hasContact = info != null && (isNotBlank(info.email()) || isNotBlank(info.phone()));
        double ratio = (hasName ? 0.5 : 0) + (hasContact ? 0.5 : 0);
        String detail = hasName && hasContact
                ? "Name and contact details are present."
                : "Add your name and an email or phone number to your profile.";
        return new AtsCheckResult("CONTACT_INFO_PRESENT", "Contact information", 15, ratio, detail);
    }

    @SuppressWarnings("unchecked")
    private AtsCheckResult summaryPresent(Map<String, Object> content) {
        Object summary = content == null ? null : content.get("summary");
        String text = summary instanceof Map<?, ?> m ? asText(((Map<String, Object>) m).get("text")) : null;
        boolean present = isNotBlank(text);
        return new AtsCheckResult("SUMMARY_PRESENT", "Professional summary", 10, present ? 1.0 : 0.0,
                present ? "A summary is present." : "No summary was generated.");
    }

    private AtsCheckResult experienceSectionPresent(Map<String, Object> content) {
        List<?> bullets = listField(content, "experienceBullets");
        boolean present = bullets != null && !bullets.isEmpty();
        return new AtsCheckResult("EXPERIENCE_SECTION_PRESENT", "Experience section", 15, present ? 1.0 : 0.0,
                present ? "Experience content is present." : "No experience content was generated.");
    }

    private AtsCheckResult dateConsistency(List<ExperienceDto> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return new AtsCheckResult("DATE_CONSISTENCY", "Date consistency", 15, 0.0,
                    "No experience entries to check.");
        }
        int consistent = 0;
        for (ExperienceDto exp : experiences) {
            if (isDateConsistent(exp)) {
                consistent++;
            }
        }
        double ratio = (double) consistent / experiences.size();
        return new AtsCheckResult("DATE_CONSISTENCY", "Date consistency", 15, ratio,
                consistent + " of " + experiences.size() + " experience entries have consistent, parseable dates.");
    }

    private boolean isDateConsistent(ExperienceDto exp) {
        YearMonth start = parseYearMonth(exp.start());
        if (start == null) {
            return false;
        }
        if (exp.current()) {
            return true;
        }
        YearMonth end = parseYearMonth(exp.end());
        return end != null && !end.isBefore(start);
    }

    private YearMonth parseYearMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim(), YEAR_MONTH);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private AtsCheckResult bulletLengthSuitability(Map<String, Object> content) {
        List<Object> groups = (List<Object>) listField(content, "experienceBullets");
        if (groups == null || groups.isEmpty()) {
            return new AtsCheckResult("BULLET_LENGTH_SUITABILITY", "Bullet length", 15, 0.0, "No bullets to check.");
        }
        int total = 0;
        int suitable = 0;
        for (Object groupObj : groups) {
            if (!(groupObj instanceof Map<?, ?> group)) continue;
            Object bulletsObj = ((Map<String, Object>) group).get("bullets");
            if (!(bulletsObj instanceof List<?> bullets)) continue;
            for (Object bulletObj : bullets) {
                if (!(bulletObj instanceof Map<?, ?> bullet)) continue;
                String text = asText(((Map<String, Object>) bullet).get("text"));
                if (text == null) continue;
                total++;
                int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
                if (words >= 3 && words <= 40) {
                    suitable++;
                }
            }
        }
        double ratio = total == 0 ? 0.0 : (double) suitable / total;
        return new AtsCheckResult("BULLET_LENGTH_SUITABILITY", "Bullet length", 15, ratio,
                total == 0 ? "No bullets to check." : suitable + " of " + total + " bullets are a suitable length.");
    }

    @SuppressWarnings("unchecked")
    private AtsCheckResult keywordPresence(Map<String, Object> content, List<String> jdKeywords) {
        if (jdKeywords == null || jdKeywords.isEmpty()) {
            return new AtsCheckResult("KEYWORD_PRESENCE", "Keyword presence", 15, 1.0,
                    "The job description had no extracted keywords to check against.");
        }
        String haystack = ResumeContentText.flatten(content).toLowerCase(Locale.ROOT);
        long present = jdKeywords.stream().filter(k -> isNotBlank(k) && haystack.contains(k.toLowerCase(Locale.ROOT))).count();
        double ratio = (double) present / jdKeywords.size();
        return new AtsCheckResult("KEYWORD_PRESENCE", "Keyword presence", 15, ratio,
                present + " of " + jdKeywords.size() + " job-description keywords appear in the generated content.");
    }

    private AtsCheckResult groundingIntegrity(Map<String, Object> grounding) {
        if (grounding == null) {
            return new AtsCheckResult("GROUNDING_INTEGRITY", "Grounding integrity", 15, 0.0, "No grounding report available.");
        }
        Object passed = grounding.get("passed");
        if (Boolean.TRUE.equals(passed)) {
            return new AtsCheckResult("GROUNDING_INTEGRITY", "Grounding integrity", 15, 1.0,
                    "Every statement traces to an evidence ID.");
        }
        Object checkedObj = grounding.get("checkedStatements");
        Object violationsObj = grounding.get("violations");
        int checked = checkedObj instanceof Number n ? n.intValue() : 0;
        int violations = violationsObj instanceof List<?> l ? l.size() : 0;
        double ratio = checked <= 0 ? 0.0 : Math.max(0.0, 1.0 - ((double) violations / checked));
        return new AtsCheckResult("GROUNDING_INTEGRITY", "Grounding integrity", 15, ratio,
                violations + " statement(s) failed grounding.");
    }

    @SuppressWarnings("unchecked")
    private List<Object> listField(Map<String, Object> content, String field) {
        if (content == null) return null;
        Object value = content.get(field);
        return value instanceof List<?> l ? (List<Object>) l : null;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
