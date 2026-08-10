package ai.careerforge.assessment.service;

import java.util.List;
import java.util.Map;

/** Flattens generated resume content (opaque JSON from ai-service) into plain text for
 *  keyword matching. Shared by {@link AtsScoringEngine} and {@link JdFitScoringEngine}. */
final class ResumeContentText {

    private ResumeContentText() {
    }

    @SuppressWarnings("unchecked")
    static String flatten(Map<String, Object> content) {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        Object summary = content.get("summary");
        if (summary instanceof Map<?, ?> m) {
            appendText(sb, ((Map<String, Object>) m).get("text"));
        }
        Object groups = content.get("experienceBullets");
        if (groups instanceof List<?> groupList) {
            for (Object groupObj : groupList) {
                if (!(groupObj instanceof Map<?, ?> group)) continue;
                Object bulletsObj = ((Map<String, Object>) group).get("bullets");
                if (bulletsObj instanceof List<?> bullets) {
                    for (Object bulletObj : bullets) {
                        if (bulletObj instanceof Map<?, ?> bullet) {
                            appendText(sb, ((Map<String, Object>) bullet).get("text"));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendText(StringBuilder sb, Object value) {
        if (value != null) {
            sb.append(String.valueOf(value)).append(' ');
        }
    }
}
