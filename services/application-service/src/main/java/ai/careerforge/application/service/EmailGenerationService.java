package ai.careerforge.application.service;

import ai.careerforge.application.client.AiServiceClient;
import ai.careerforge.application.client.ClientDtos.EmailContentRequest;
import ai.careerforge.application.client.ClientDtos.EmailContentResponse;
import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.ProfileDto;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.EmailContent;
import ai.careerforge.application.domain.GenerationType;
import ai.careerforge.application.repository.EmailContentRepository;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates email-content generation for an {@link Application}: fetches evidence and the
 * candidate's stated name, calls ai-service for a grounded body, deterministically assembles
 * the final subject and appends the candidate's real name, then persists the result and
 * attaches it to the application. See ARCHITECTURE_DECISIONS.md ADR-019.
 *
 * <p>Only the greeting/body/closing/sign-off prose comes from the model — the subject line
 * and the signature name are built here from data already verified elsewhere (the
 * application's denormalised job title/company, the candidate's own profile), never
 * generated, so the two facts most load-bearing for "is this the right email" can never be
 * hallucinated.
 */
@Service
public class EmailGenerationService {

    private static final Logger log = LoggerFactory.getLogger(EmailGenerationService.class);

    private static final String DEFAULT_GREETING = "Dear Hiring Manager,";
    private static final String DEFAULT_SIGN_OFF = "Sincerely,";

    private final ApplicationService applicationService;
    private final ProfileServiceClient profileServiceClient;
    private final AiServiceClient aiServiceClient;
    private final EmailContentRepository emailContents;
    private final ObjectMapper objectMapper;

    public EmailGenerationService(ApplicationService applicationService, ProfileServiceClient profileServiceClient,
                                  AiServiceClient aiServiceClient, EmailContentRepository emailContents,
                                  ObjectMapper objectMapper) {
        this.applicationService = applicationService;
        this.profileServiceClient = profileServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.emailContents = emailContents;
        this.objectMapper = objectMapper;
    }

    /** Generates (or regenerates) the email for an application. Each call creates a new
     *  immutable {@link EmailContent} version and repoints {@code Application.emailId} at it —
     *  the same regenerate-by-calling-again pattern resume-service uses. */
    public EmailContent generate(String userId, String applicationId) {
        Application application = applicationService.requireOwned(userId, applicationId);
        requireEmailOnly(application);
        requireJobTitle(application);

        List<EvidenceItem> evidence = fetchEvidence();
        if (evidence.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add at least one experience to your profile before generating an email.");
        }
        String candidateName = fetchCandidateName();

        EmailContentResponse response = callAi(application, evidence);
        Map<String, Object> content = toMap(response.content());

        String greeting = stringField(content, "greeting");
        Map<String, Object> bodyParagraph = mapField(content, "bodyParagraph");
        Map<String, Object> closingParagraph = mapField(content, "closingParagraph");
        String signOff = stringField(content, "signOff");

        List<Map<String, Object>> highlights = new ArrayList<>();
        if (bodyParagraph != null) {
            highlights.add(bodyParagraph);
        }
        if (closingParagraph != null) {
            highlights.add(closingParagraph);
        }

        String subject = buildSubject(application, candidateName);
        String body = buildBody(application, greeting, textOf(bodyParagraph), textOf(closingParagraph),
                signOff, candidateName);

        int nextVersion = emailContents
                .findTopByApplicationIdAndUserIdOrderByVersionDesc(applicationId, userId)
                .map(existing -> existing.version() + 1)
                .orElse(1);

        EmailContent saved = emailContents.save(new EmailContent(
                applicationId, userId, nextVersion, subject, body, highlights,
                toMap(response.grounding()), response.removedParagraphs(),
                provenanceField(response, "promptVersion"), provenanceField(response, "model")));

        applicationService.attachEmail(userId, applicationId, saved.id());

        return saved;
    }

    /** Latest version — 404 if the application has never had an email generated. */
    public EmailContent requireLatest(String userId, String applicationId) {
        applicationService.requireOwned(userId, applicationId);
        return emailContents.findTopByApplicationIdAndUserIdOrderByVersionDesc(applicationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- guards ----

    private void requireEmailOnly(Application application) {
        if (application.generationType() != GenerationType.EMAIL_ONLY) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This application's generation type does not include email generation.");
        }
    }

    private void requireJobTitle(Application application) {
        if (!hasText(application.jobTitle())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This job description doesn't have a confirmed title yet. Confirm and "
                            + "analyze the job description before generating an email.");
        }
    }

    // --------------------------------------------------------- deterministic assembly ----

    /** Never generated by the model — built only from the application's already-verified
     *  denormalised job title/company and the candidate's own stated name. */
    private String buildSubject(Application application, String candidateName) {
        String role = hasText(application.jobTitle()) ? application.jobTitle() : null;
        String company = hasText(application.company()) ? application.company() : null;

        StringBuilder sb = new StringBuilder();
        if (role != null) {
            sb.append("Application for ").append(role);
            if (company != null) {
                sb.append(" at ").append(company);
            }
        } else if (company != null) {
            sb.append("Job Application - ").append(company);
        } else {
            sb.append("Job Application");
        }
        if (hasText(candidateName)) {
            sb.append(" - ").append(candidateName);
        }
        return sb.toString();
    }

    /** Assembles the final body around the model's grounded paragraphs. A paragraph the
     *  degrade path removed (both attempts failed grounding) falls back to a deterministic
     *  sentence built only from already-verified data — the email is never left with a gap,
     *  and the fallback never states anything the model wasn't already allowed to state
     *  freely. */
    private String buildBody(Application application, String greeting, String bodyText, String closingText,
                             String signOff, String candidateName) {
        StringBuilder sb = new StringBuilder();
        sb.append(hasText(greeting) ? greeting : DEFAULT_GREETING).append("\n\n");
        sb.append(hasText(bodyText) ? bodyText : fallbackBody(application)).append("\n\n");
        sb.append(hasText(closingText) ? closingText : fallbackClosing()).append("\n\n");
        sb.append(hasText(signOff) ? signOff : DEFAULT_SIGN_OFF);
        if (hasText(candidateName)) {
            sb.append('\n').append(candidateName);
        }
        return sb.toString();
    }

    private String fallbackBody(Application application) {
        String role = hasText(application.jobTitle()) ? application.jobTitle() : "this position";
        String companyClause = hasText(application.company()) ? " at " + application.company() : "";
        return "I am writing to express my interest in the " + role + " role" + companyClause + ".";
    }

    private String fallbackClosing() {
        return "My resume is attached for your review, and I would welcome the opportunity to "
                + "discuss this role further.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // ------------------------------------------------------------- upstream calls ----

    private List<EvidenceItem> fetchEvidence() {
        try {
            return profileServiceClient.getEvidence();
        } catch (FeignException ex) {
            log.warn("profile-service evidence call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    /** Best-effort — a missing or unreadable name never blocks generation; the signature is
     *  simply omitted rather than inventing one. */
    private String fetchCandidateName() {
        try {
            ProfileDto profile = profileServiceClient.getProfile();
            return profile.personalInformation() == null ? null : profile.personalInformation().fullName();
        } catch (FeignException ex) {
            log.info("profile-service personal-information call failed, omitting signature name: {}",
                    ex.getMessage());
            return null;
        }
    }

    private EmailContentResponse callAi(Application application, List<EvidenceItem> evidence) {
        try {
            return aiServiceClient.generateEmailContent(
                    new EmailContentRequest(application.jobTitle(), application.company(), evidence, null));
        } catch (FeignException.BadGateway | FeignException.ServiceUnavailable ex) {
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        } catch (FeignException ex) {
            log.warn("ai-service email-content call failed for applicationId={}: {}", application.id(), ex.getMessage());
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    // ---------------------------------------------------------------- json helpers ----

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() { });
    }

    private String stringField(Map<String, Object> content, String field) {
        Object value = content.get(field);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapField(Map<String, Object> content, String field) {
        Object value = content.get(field);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private String textOf(Map<String, Object> paragraph) {
        return paragraph == null ? null : stringField(paragraph, "text");
    }

    private String provenanceField(EmailContentResponse response, String field) {
        JsonNode provenance = response.provenance();
        if (provenance == null || !provenance.hasNonNull(field)) {
            return null;
        }
        return provenance.get(field).asText();
    }
}
