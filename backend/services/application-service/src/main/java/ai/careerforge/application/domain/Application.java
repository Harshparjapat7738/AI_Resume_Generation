package ai.careerforge.application.domain;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The central aggregate that ties one job application together: which job it's for, what was
 * asked to be generated, and references to the artifacts and scores that resulted. See
 * docs/DATABASE.md &sect;3 (careerforge_application) and ARCHITECTURE_DECISIONS.md ADR-017.
 *
 * <p>This document stores <strong>references only</strong>, never a copy of the data another
 * service owns:
 * <ul>
 *   <li>{@code resumeVersionId} points at resume-service's immutable {@code resume_versions}
 *       document — the resume content itself is never duplicated here.</li>
 *   <li>{@code resumeVersionId} doubles as the assessment reference: assessment-service keys
 *       both the ATS and JD-fit assessment for a resume uniquely by
 *       {@code resumeVersionId + userId} (ADR-010), and exposes no separate assessment ID —
 *       so there is nothing further to store. {@code assessed} records only whether one was
 *       found at the time the resume was attached.</li>
 *   <li>{@code coverLetterVersionId} is a reserved field for the cover-letter pipeline —
 *       always {@code null} today (see {@link GenerationType}). {@code emailId} points at
 *       this service's own {@code emails} collection (see {@code EmailContent}) once email
 *       content has been generated — see ARCHITECTURE_DECISIONS.md ADR-019.</li>
 *   <li>{@code templateId}, if supplied, is validated at creation time against
 *       resume-service's real template catalogue (its {@code GET /api/resumes/templates/{id}},
 *       ADR-016) — the same 404-for-unowned rule resume-service itself applies at generation
 *       time.</li>
 * </ul>
 *
 * <p>{@code jobTitle}/{@code company} are denormalised from jd-service at creation time, the
 * same pattern {@code resume-service}'s {@code ResumeVersion} already uses, so a dashboard
 * listing doesn't need a cross-service call per row.
 */
@Document(collection = "applications")
public class Application {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("jobDescriptionId")
    private String jobDescriptionId;

    @Field("jobTitle")
    private String jobTitle;

    @Field("company")
    private String company;

    @Field("generationType")
    private GenerationType generationType;

    @Field("templateId")
    private String templateId;

    @Field("resumeVersionId")
    private String resumeVersionId;

    /** Reserved — always null until cover-letter generation exists. */
    @Field("coverLetterVersionId")
    private String coverLetterVersionId;

    /** Reserved — always null until email-content generation exists. */
    @Field("emailId")
    private String emailId;

    /** Whether assessment-service had a scored assessment for {@code resumeVersionId} the
     *  last time it was attached. Best-effort — a missing assessment never blocks attaching
     *  the resume, the same way the frontend's own assessment call is non-fatal. */
    @Field("assessed")
    private boolean assessed;

    @Field("status")
    private ApplicationStatus status;

    @Field("failureCode")
    private String failureCode;

    /**
     * Per-output failure reasons for {@code GenerationType.ALL} (and, harmlessly, any other
     * type): {@code resumeVersionId}/{@code coverLetterVersionId}/{@code emailId} being null
     * already distinguishes "not generated" from "generated", but not "never attempted" from
     * "attempted and failed", and never carries a reason. These three are cleared the moment
     * their output attaches successfully and are the only thing a client needs to render
     * "Resume &#10003; | Cover Letter &#10003; | Email &#10007; — generation failed" and to
     * survive a refresh — see {@link #recordOutputFailure}.
     */
    @Field("resumeError")
    private String resumeError;

    @Field("coverLetterError")
    private String coverLetterError;

    @Field("emailError")
    private String emailError;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Version
    @Field("version")
    private Long entityVersion;

    protected Application() {
        // Spring Data
    }

    public Application(String userId, String jobDescriptionId, String jobTitle, String company,
                       GenerationType generationType, String templateId) {
        this.userId = userId;
        this.jobDescriptionId = jobDescriptionId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.generationType = generationType;
        this.templateId = templateId;
        this.status = ApplicationStatus.DRAFT;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String jobDescriptionId() {
        return jobDescriptionId;
    }

    public String jobTitle() {
        return jobTitle;
    }

    public String company() {
        return company;
    }

    public GenerationType generationType() {
        return generationType;
    }

    public String templateId() {
        return templateId;
    }

    public String resumeVersionId() {
        return resumeVersionId;
    }

    public String coverLetterVersionId() {
        return coverLetterVersionId;
    }

    public String emailId() {
        return emailId;
    }

    public boolean assessed() {
        return assessed;
    }

    public ApplicationStatus status() {
        return status;
    }

    public String failureCode() {
        return failureCode;
    }

    public String resumeError() {
        return resumeError;
    }

    public String coverLetterError() {
        return coverLetterError;
    }

    public String emailError() {
        return emailError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Attaches the generated resume reference and recomputes status — see
     *  {@link #deriveStatus()}. */
    public void attachResume(String resumeVersionId, boolean assessed) {
        this.resumeVersionId = resumeVersionId;
        this.assessed = assessed;
        this.failureCode = null;
        this.resumeError = null;
        this.status = deriveStatus();
    }

    /** Attaches (or replaces, on regenerate) the generated email reference and recomputes
     *  status — see {@link #deriveStatus()}. */
    public void attachEmail(String emailId) {
        this.emailId = emailId;
        this.failureCode = null;
        this.emailError = null;
        this.status = deriveStatus();
    }

    /** Attaches (or replaces, on regenerate) the generated cover-letter reference and
     *  recomputes status — see {@link #deriveStatus()}. */
    public void attachCoverLetter(String coverLetterVersionId) {
        this.coverLetterVersionId = coverLetterVersionId;
        this.failureCode = null;
        this.coverLetterError = null;
        this.status = deriveStatus();
    }

    /**
     * Records why one specific output failed to generate — used by {@code GenerationType.ALL}
     * (and harmlessly usable by any type) so a failed output is independently visible and
     * independently retryable, without the whole {@link Application} being marked
     * {@link ApplicationStatus#FAILED}: {@link #status} keeps following {@link #deriveStatus()}
     * exactly as before (this method never changes it), so the other outputs can still
     * complete and {@code COMPLETED} still requires every output this generation type needs —
     * see {@link #deriveStatus()}.
     *
     * @param output one of {@code "resume"}, {@code "coverLetter"}, {@code "email"}
     */
    public void recordOutputFailure(String output, String reason) {
        switch (output) {
            case "resume" -> this.resumeError = reason;
            case "coverLetter" -> this.coverLetterError = reason;
            case "email" -> this.emailError = reason;
            default -> throw new IllegalArgumentException("Unknown output: " + output);
        }
    }

    /**
     * Whether every artifact {@link #generationType} requires now exists. {@code RESUME_ONLY}
     * needs only the resume; {@code EMAIL_ONLY} needs only the email; {@code COVER_LETTER_ONLY}
     * needs only the letter; {@code ALL} needs every one of the three — so it can only reach
     * {@code COMPLETED} once resume, cover letter and email have each attached successfully,
     * however many attempts and retries that took (see {@link #recordOutputFailure}, which
     * never touches {@link #status} itself).
     */
    private ApplicationStatus deriveStatus() {
        boolean resumeSatisfied = resumeVersionId != null;
        boolean emailSatisfied = emailId != null;
        boolean coverLetterSatisfied = coverLetterVersionId != null;

        boolean complete = switch (generationType) {
            case RESUME_ONLY -> resumeSatisfied;
            case EMAIL_ONLY -> emailSatisfied;
            case COVER_LETTER_ONLY -> coverLetterSatisfied;
            case ALL -> resumeSatisfied && emailSatisfied && coverLetterSatisfied;
        };
        if (complete) {
            return ApplicationStatus.COMPLETED;
        }
        return (resumeSatisfied || emailSatisfied || coverLetterSatisfied)
                ? ApplicationStatus.PROCESSING
                : ApplicationStatus.DRAFT;
    }

    public void transitionTo(ApplicationStatus next) {
        this.status = next;
        if (next != ApplicationStatus.FAILED) {
            this.failureCode = null;
        }
    }

    public void fail(String failureCode) {
        this.status = ApplicationStatus.FAILED;
        this.failureCode = failureCode;
    }
}
