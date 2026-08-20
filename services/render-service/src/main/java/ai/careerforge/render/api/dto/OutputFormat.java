package ai.careerforge.render.api.dto;

/** The only output render-service produces (ADR-036): PDF. No DOCX, no mail-merge. A single
 *  value today, kept as an explicit enum rather than assumed, so the contract states the
 *  constraint instead of leaving it implicit. */
public enum OutputFormat {
    PDF
}
