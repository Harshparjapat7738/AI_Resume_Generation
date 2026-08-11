package ai.careerforge.document.docx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fixed set of profile-derived fields a custom template's placeholders/regions can map
 * onto — the same fields {@code ResumeRenderModel} already exposes for the built-in pipeline,
 * so a custom template ultimately draws from exactly the same real data, nothing extra. Each
 * key carries a small synonym list (deterministic, normalized string matching — no AI call)
 * used to auto-suggest a mapping for a detected {@code {{token}}} or a heading-styled
 * paragraph; the caller always gets to review/override the suggestion before it's saved.
 */
public final class ProfileFieldCatalog {

    private ProfileFieldCatalog() {
    }

    public static final String NAME = "NAME";
    public static final String EMAIL = "EMAIL";
    public static final String PHONE = "PHONE";
    public static final String HEADLINE = "HEADLINE";
    public static final String SUMMARY = "SUMMARY";
    public static final String EXPERIENCE = "EXPERIENCE";
    public static final String EDUCATION = "EDUCATION";
    public static final String SKILLS = "SKILLS";
    public static final String PROJECTS = "PROJECTS";
    public static final String CERTIFICATIONS = "CERTIFICATIONS";
    public static final String ACHIEVEMENTS = "ACHIEVEMENTS";
    public static final String LINKS = "LINKS";

    /** Display label for each key, for the frontend mapping editor. */
    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    private static final Map<String, String[]> SYNONYMS = new LinkedHashMap<>();

    static {
        register(NAME, "Full Name", "name", "fullname", "full_name", "candidatename", "applicantname");
        register(EMAIL, "Email", "email", "emailaddress", "mail");
        register(PHONE, "Phone", "phone", "mobile", "contact", "phonenumber", "telephone", "cell");
        register(HEADLINE, "Professional Headline", "headline", "title", "role", "jobtitle", "position");
        register(SUMMARY, "Professional Summary", "summary", "objective", "profile", "aboutme", "about");
        register(EXPERIENCE, "Experience", "experience", "workexperience", "employment", "worksexperience", "jobs", "career");
        register(EDUCATION, "Education", "education", "academics", "qualifications", "academicbackground");
        register(SKILLS, "Skills", "skills", "technicalskills", "coreskills", "competencies", "expertise");
        register(PROJECTS, "Projects", "projects", "portfolio", "personalprojects");
        register(CERTIFICATIONS, "Certifications", "certifications", "certificates", "licenses");
        register(ACHIEVEMENTS, "Achievements", "achievements", "awards", "honors", "accomplishments");
        register(LINKS, "Links", "links", "portfolio_link", "website", "socials", "profiles");
    }

    private static void register(String key, String label, String... synonyms) {
        LABELS.put(key, label);
        SYNONYMS.put(key, synonyms);
    }

    /** Normalizes a raw token/heading (lowercase, strip everything but letters/digits) and
     *  matches it against every field's synonym list. {@code null} if nothing matches —
     *  callers must never silently fall back to a default field, since a wrong auto-mapping
     *  would misplace the user's real data in the generated document. */
    public static String suggest(String rawText) {
        if (rawText == null) return null;
        String normalized = normalize(rawText);
        if (normalized.isEmpty()) return null;
        for (Map.Entry<String, String[]> entry : SYNONYMS.entrySet()) {
            for (String synonym : entry.getValue()) {
                if (normalized.equals(synonym) || normalized.contains(synonym) || synonym.contains(normalized)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
