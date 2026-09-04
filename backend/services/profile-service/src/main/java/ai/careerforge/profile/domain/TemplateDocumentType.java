package ai.careerforge.profile.domain;

/**
 * What kind of document this template is meant for — purely descriptive metadata shown in the
 * UI and carried into the external-generation handoff (the selected-template reference injected
 * into the ChatGPT prompt); CareerForge itself never branches rendering behaviour on it, since
 * it never renders anything from a template at all (ADR-033).
 */
public enum TemplateDocumentType {
    RESUME,
    COVER_LETTER,
    BOTH
}
