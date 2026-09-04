package ai.careerforge.application.document;

import java.util.List;

/**
 * Reusable {@link ResumeDocumentModel}/{@link CoverLetterDocumentModel} fixtures shared by the
 * round-trip and {@link DocumentEvidenceValidator} tests.
 *
 * <ul>
 *   <li>{@link #shortResume()} — the minimal valid shape: one header, one section, one entry,
 *       one bullet, no summary.</li>
 *   <li>{@link #longResume()} — every section type, a professional summary, and content
 *       deliberately hostile to naive string handling: HTML-looking tags, an inline
 *       {@code <script>} fragment, curly and straight apostrophes, ampersands, em-dashes,
 *       non-Latin script (Portuguese/Japanese/Cyrillic place names), and one bullet several
 *       hundred characters long — proving the model round-trips arbitrary candidate text
 *       byte-for-byte rather than merely the ASCII happy path.</li>
 *   <li>{@link #coverLetter()} — a minimal valid {@link CoverLetterDocumentModel}.</li>
 * </ul>
 */
final class DocumentModelFixtures {

    private DocumentModelFixtures() {
    }

    static final String SCHEMA_VERSION = "1.0.0";

    static DocumentHeader header() {
        return new DocumentHeader("Priya Sharma", "priya.sharma@example.com", "+1 415 555 0100",
                "San Francisco, CA", List.of(new HeaderLink("LinkedIn", "https://linkedin.com/in/priyasharma")));
    }

    static RenderHints renderHints() {
        return new RenderHints(PageSize.A4, 1, FontFamily.ARIAL, null);
    }

    static ResumeDocumentModel shortResume() {
        SectionEntry entry = new SectionEntry("EXP-001", "Software Engineer", "Acme Corp", "Remote",
                "2022-01", "Present",
                List.of(new ContentLeaf("Built and shipped a payments service handling 10,000 transactions per day.",
                        List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));

        return new ResumeDocumentModel(SCHEMA_VERSION, header(), null,
                List.of(new ResumeSection(SectionHeading.EXPERIENCE, List.of(entry))),
                renderHints(), GapReport.empty());
    }

    /** ~700 characters, one leaf — the "one long bullet" fixture requirement. */
    static final String LONG_BULLET =
            "Redesigned the core payments-processing pipeline end-to-end — replacing a monolithic "
            + "batch job with an event-driven architecture built on Kafka, Go microservices and a "
            + "Postgres-backed ledger — which cut end-of-day reconciliation time from roughly six "
            + "hours to under twelve minutes, eliminated a recurring class of double-charge incidents "
            + "that had been costing the finance team dozens of manual-refund hours every month, and "
            + "gave the on-call rotation a dashboard that surfaced anomalies within seconds instead of "
            + "the next morning's batch report; the same architecture was later adopted, largely "
            + "unchanged, by two adjacent teams handling payouts and subscription billing.";

    static ResumeDocumentModel longResume() {
        ContentLeaf summary = new ContentLeaf(
                "Backend engineer with 6+ years building distributed systems — previously at "
                + "O'Brien & Sons in São Paulo, with stints in 東京 (Tokyo) and Москва "
                + "(Moscow). Comfortable across <Kubernetes/> style tooling, \"quoted\" architecture "
                + "reviews & CI/CD pipelines.",
                List.of("EXP-001", "EXP-002"), ContentOrigin.REPHRASED_FROM_PROFILE);

        SectionEntry experience1 = new SectionEntry("EXP-001", "Senior Backend Engineer", "O'Brien & Sons",
                "São Paulo, Brazil", "2022-01", "Present", List.of(
                        new ContentLeaf(LONG_BULLET, List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE),
                        new ContentLeaf(
                                "Patched a reporting job that mishandled <script>alert('x')</script> input "
                                + "& introduced O'Brien's new escaping rules — reduced incident count by 40%.",
                                List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));

        SectionEntry experience2 = new SectionEntry("EXP-002", "Backend Engineer", "Tokyo Systems K.K.",
                "東京, Japan", "2019-06", "2021-12", List.of(
                        new ContentLeaf("Led the 東京 team's migration to Kubernetes — cut deploy "
                                + "time from 45 minutes to 6.",
                                List.of("EXP-002"), ContentOrigin.REPHRASED_FROM_PROFILE)));

        SectionEntry education = new SectionEntry("EDU-001", "B.Tech in Computer Science",
                "Indian Institute of Technology", "Mumbai, India", "2014", "2018", List.of());

        SectionEntry skill = new SectionEntry("SKILL-001", "Go, Kafka & Kubernetes", null, null, null, null,
                List.of(new ContentLeaf("Used across systems serving traffic from Москва "
                                + "to São Paulo.",
                        List.of("SKILL-001"), ContentOrigin.VERBATIM_FROM_PROFILE)));

        SectionEntry project = new SectionEntry("PROJ-001", "Open-source \"FastQueue\" library", null,
                null, "2021", "2022", List.of(
                        new ContentLeaf("Maintained a queue library adopted by 1,200+ GitHub stars' worth "
                                + "of downstream projects.",
                                List.of("PROJ-001"), ContentOrigin.REPHRASED_FROM_PROFILE)));

        SectionEntry certification = new SectionEntry("CERT-001",
                "AWS Certified Solutions Architect — Professional", "Amazon Web Services",
                null, "2023", null, List.of());

        SectionEntry achievement = new SectionEntry("ACH-001", "Speaker, QCon São Paulo", null,
                null, "2023", null, List.of(
                        new ContentLeaf("Presented \"Scaling Kafka at O'Brien & Sons\" to 500+ attendees.",
                                List.of("ACH-001"), ContentOrigin.VERBATIM_FROM_PROFILE)));

        return new ResumeDocumentModel(SCHEMA_VERSION, header(), summary, List.of(
                new ResumeSection(SectionHeading.EXPERIENCE, List.of(experience1, experience2)),
                new ResumeSection(SectionHeading.EDUCATION, List.of(education)),
                new ResumeSection(SectionHeading.SKILLS, List.of(skill)),
                new ResumeSection(SectionHeading.PROJECTS, List.of(project)),
                new ResumeSection(SectionHeading.CERTIFICATIONS, List.of(certification)),
                new ResumeSection(SectionHeading.ACHIEVEMENTS, List.of(achievement))),
                new RenderHints(PageSize.A4, 2, FontFamily.GEORGIA, "#1A2B3C"), GapReport.empty());
    }

    static CoverLetterDocumentModel coverLetter() {
        List<ContentLeaf> paragraphs = List.of(
                new ContentLeaf("I'm writing to apply for the Senior Backend Engineer role — in my "
                        + "current position I led the redesign of a payments pipeline handling millions "
                        + "of transactions a day.",
                        List.of("EXP-001"), ContentOrigin.REPHRASED_FROM_PROFILE),
                new ContentLeaf("My resume is attached for your review, and I would welcome the chance "
                        + "to discuss this role further.",
                        List.of("EXP-001"), ContentOrigin.VERBATIM_FROM_PROFILE));

        return new CoverLetterDocumentModel(SCHEMA_VERSION, header(), "Senior Backend Engineer",
                "Acme & Sons", "Dear Hiring Manager,", paragraphs, "Sincerely,", "Priya Sharma",
                renderHints(), GapReport.empty());
    }
}
