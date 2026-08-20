package ai.careerforge.application.document;

/**
 * The closed, ATS-standard set of resume section headings (ADR-036) — deliberately not a free
 * string. A resume-parsing ATS looks for a small, conventional vocabulary; a creative heading
 * ("What I Bring") is a parsing risk a candidate never chose to take, so
 * {@code application-service}'s deterministic assembly may only pick from this enum, never
 * accept an arbitrary label from anywhere upstream (ai-service included).
 *
 * <p>Each value corresponds exactly to one of {@code EvidenceItem}'s own {@code type} codes
 * (profile-service's six evidence-bearing sections: {@code EXP}, {@code EDU}, {@code SKILL},
 * {@code PROJ}, {@code CERT}, {@code ACH}) — a resume section only ever holds entries whose
 * {@code evidenceId} matches that same code, so the heading and the evidence it presents can
 * never drift apart. The one resume element that is <em>not</em> a closed-vocabulary section —
 * the professional summary, which synthesises across several evidence items rather than
 * presenting one type — is {@link ResumeDocumentModel#summary()}, deliberately outside this
 * enum and outside {@code sections} entirely.
 */
public enum SectionHeading {

    EXPERIENCE("Experience"),
    EDUCATION("Education"),
    SKILLS("Skills"),
    PROJECTS("Projects"),
    CERTIFICATIONS("Certifications"),
    ACHIEVEMENTS("Achievements");

    private final String atsLabel;

    SectionHeading(String atsLabel) {
        this.atsLabel = atsLabel;
    }

    /** The literal heading text rendered on the document — plain and conventional on purpose. */
    public String atsLabel() {
        return atsLabel;
    }
}
