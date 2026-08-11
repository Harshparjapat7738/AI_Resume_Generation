package ai.careerforge.document.domain;

/**
 * One {@code {{token}}} placeholder found in the uploaded document, plus a short excerpt of
 * the surrounding paragraph text so a user reviewing the mapping editor can tell which one is
 * which without opening the file. {@code suggestedField} is this analyzer's best deterministic
 * guess (see {@code docx.ProfileFieldCatalog}) — {@code null} if nothing matched — and is only
 * ever a suggestion: the caller decides the real mapping.
 */
public record DetectedField(String token, String context, String suggestedField) {
}
