package ai.careerforge.document.render;

import ai.careerforge.document.render.ResumeRenderModel.AchievementEntry;
import ai.careerforge.document.render.ResumeRenderModel.CertificationEntry;
import ai.careerforge.document.render.ResumeRenderModel.EducationEntry;
import ai.careerforge.document.render.ResumeRenderModel.ExperienceEntry;
import ai.careerforge.document.render.ResumeRenderModel.ProjectEntry;
import java.util.List;

/**
 * Canned, clearly-fictional sample data used only for the Templates page's "what will my
 * resume actually look like in this template" preview (see {@code DocumentRenderService
 * #renderPreview} / {@code CustomTemplateAssetService#generatePreview}) — never a real user's
 * data, and never persisted anywhere. Fills every section {@link ResumeRenderModel} defines so
 * every template's every section renders something, regardless of which sections that
 * particular template chooses to show (see the three built-in Thymeleaf templates and
 * {@code DocxMailMerge}/{@code PdfMailMerge}'s field resolution, which all read this exact
 * shape). The same fixed identity ("Alex Johnson") every time — this is deliberately not
 * randomised, so the same template always renders the same preview and two renders of the same
 * template are byte-identical.
 */
public final class SampleResumeRenderModel {

    private SampleResumeRenderModel() {
    }

    public static ResumeRenderModel sample() {
        return new ResumeRenderModel(
                "Alex Johnson",
                "Senior Software Engineer",
                "alex.johnson@email.com",
                "+1 (555) 123-4567",
                List.of("linkedin.com/in/alexjohnson", "github.com/alexjohnson"),
                "Senior Software Engineer",
                "TechCorp Inc.",
                "Results-driven software engineer with 6+ years building scalable backend systems "
                        + "and leading cross-functional teams. Proven track record of shipping high-impact "
                        + "features, mentoring engineers, and improving system reliability.",
                List.of(
                        new ExperienceEntry(
                                "TechCorp Inc.", "Senior Software Engineer", "San Francisco, CA", "2021 – Present",
                                List.of(
                                        "Led the redesign of the core billing service, reducing latency by 45% for 2M+ users",
                                        "Mentored a team of 4 engineers and established the team's code review standards",
                                        "Migrated legacy infrastructure to Kubernetes, cutting deployment time from 40 to 6 minutes"),
                                List.of("Java", "Spring Boot", "Kubernetes", "PostgreSQL")),
                        new ExperienceEntry(
                                "Initio Labs", "Software Engineer", "Austin, TX", "2018 – 2021",
                                List.of(
                                        "Built and shipped the company's first public REST API, adopted by 50+ partners",
                                        "Reduced production incident rate by 30% by introducing automated integration tests"),
                                List.of("Node.js", "React", "AWS"))),
                List.of(new EducationEntry(
                        "Stanford University", "B.Sc. in Computer Science", null, "2014 – 2018",
                        null, null)),
                List.of("Java", "Spring Boot", "Kubernetes", "PostgreSQL", "React", "AWS", "System Design"),
                List.of(new ProjectEntry(
                        "Open-source rate limiter",
                        "A distributed, Redis-backed rate limiter used by several small SaaS teams.",
                        "Creator", List.of("Go", "Redis"), "github.com/alexjohnson/ratelimiter", null, "2022")),
                List.of(new CertificationEntry(
                        "AWS Certified Solutions Architect", "Amazon Web Services", "2022", null, null, null)),
                List.of(new AchievementEntry(
                        "Speaker, backend engineering meetup",
                        "Presented \"Scaling billing systems\" to 200+ engineers.", "2023")));
    }
}
