package ai.careerforge.document.render;

import java.util.List;

/**
 * Everything a resume template needs to render, already merged and formatted — no template
 * touches the raw AI content map or a client DTO directly. Two kinds of data feed this:
 *
 * <ul>
 *   <li><b>AI-authored, grounded text</b> — {@code summary} and each experience/project's
 *       bullets — comes from the already-verified {@code ResumeVersion.content} produced by
 *       ai-service's grounding pipeline.</li>
 *   <li><b>Factual biographical data</b> — name, contact, dates, institutions, employers,
 *       certifications, achievements — comes straight from the user's own profile, never
 *       from the model, since nothing about "I graduated from MIT in 2019" needs grounding
 *       against evidence the user themselves supplied it as.</li>
 * </ul>
 */
public record ResumeRenderModel(
        String fullName,
        String headline,
        String email,
        String phone,
        List<String> links,
        String jobTitle,
        String company,
        String summary,
        List<ExperienceEntry> experience,
        List<EducationEntry> education,
        List<String> skills,
        List<ProjectEntry> projects,
        List<CertificationEntry> certifications,
        List<AchievementEntry> achievements) {

    public record ExperienceEntry(
            String company, String title, String location, String dateRange,
            List<String> bullets, List<String> technologies) {
    }

    public record EducationEntry(
            String institution, String degree, String field, String dateRange,
            String grade, String description) {
    }

    public record ProjectEntry(
            String name, String description, String role, List<String> technologies,
            String githubUrl, String liveUrl, String dateRange) {
    }

    public record CertificationEntry(
            String name, String issuer, String issuedOn, String expiresOn,
            String credentialId, String credentialUrl) {
    }

    public record AchievementEntry(String title, String description, String date) {
    }
}
