package ai.careerforge.resume.domain;

/** Whether a template is selectable. Disabled templates stay in the catalogue (existing
 *  resumes still reference them) but never appear in listings or pass selection. */
public enum TemplateStatus {
    ACTIVE,
    DISABLED
}
