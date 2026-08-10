package ai.careerforge.resume.domain;

public enum ResumeGenerationStatus {
    QUEUED,
    SELECTING_EVIDENCE,
    GENERATING,
    VALIDATING,
    COMPLETED,
    FAILED
}
