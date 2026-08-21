package ai.careerforge.application.service;

import ai.careerforge.application.client.ClientDtos.EvidenceItem;
import ai.careerforge.application.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.application.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.application.client.ClientDtos.ProfileDto;
import ai.careerforge.application.client.ClientDtos.RenderContentLeaf;
import ai.careerforge.application.client.ClientDtos.RenderDocumentHeader;
import ai.careerforge.application.client.ClientDtos.RenderHeaderLink;
import ai.careerforge.application.client.ClientDtos.RenderHints;
import ai.careerforge.application.client.ClientDtos.RenderResponse;
import ai.careerforge.application.client.ClientDtos.RenderResumeSection;
import ai.careerforge.application.client.ClientDtos.RenderSectionEntry;
import ai.careerforge.application.client.ClientDtos.ResumeRenderRequest;
import ai.careerforge.application.client.JdServiceClient;
import ai.careerforge.application.client.ProfileServiceClient;
import ai.careerforge.application.client.RenderServiceClient;
import ai.careerforge.application.domain.Application;
import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import feign.FeignException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Connects the existing JD-optimization flow (jd-service, ADR-033) to render-service's
 * rendering pipeline (ADR-036): JD &rarr; JD optimization data &rarr; render-service &rarr; PDF.
 *
 * <p><strong>No AI call happens here and no new content is generated</strong> — this is
 * deliberately the minimum integration, not resume-content generation. Every field in the
 * assembled {@link ResumeRenderRequest} is copied verbatim from the candidate's own profile
 * evidence ({@code origin} is always {@code "VERBATIM_FROM_PROFILE"}); the JD optimization only
 * decides <em>which</em> evidence is relevant to this job (its {@code citedEvidenceIds} — the
 * same already-hallucination-stripped set {@code JdOptimizationService.stripUnknownIds}
 * produces) and what order to lead with (its {@code emphasis} ranking). ai-service's Resume
 * Content operation and a full {@code ResumeDocumentModel} assembly step (built in this module's
 * own {@code application.document} package, see its Javadoc) remain a separate, larger piece of
 * ADR-036 this integration does not implement.
 *
 * <p>Template selection is untouched: {@link Application#templateId()} continues to mean
 * exactly what it already did, unrelated to this flow. render-service has exactly one built-in
 * layout today ({@code "STANDARD"}), so that is what every render request asks for, regardless
 * of {@code templateId} — a second built-in layout arriving later is a one-line change here, not
 * a redesign.
 *
 * <p>Renders synchronously and returns the PDF bytes directly — no persistence, per this
 * integration's own scope: render-service does not yet store PDFs it produces, so there is no
 * object-store reference for {@link Application} to hold, and none is added.
 */
@Service
public class ResumeRenderService {

    private static final Logger log = LoggerFactory.getLogger(ResumeRenderService.class);

    /** Evidence types in resume section order — the same six codes profile-service's
     *  {@code EvidenceItem} always uses, mapped 1:1 to render-service's ATS-standard section
     *  headings (see {@link #sectionHeading}). */
    private static final List<String> SECTION_ORDER =
            List.of("EXPERIENCE", "EDUCATION", "SKILL", "PROJECT", "CERTIFICATION", "ACHIEVEMENT");

    private final ApplicationService applicationService;
    private final JdServiceClient jdServiceClient;
    private final ProfileServiceClient profileServiceClient;
    private final RenderServiceClient renderServiceClient;

    public ResumeRenderService(ApplicationService applicationService, JdServiceClient jdServiceClient,
                               ProfileServiceClient profileServiceClient, RenderServiceClient renderServiceClient) {
        this.applicationService = applicationService;
        this.jdServiceClient = jdServiceClient;
        this.profileServiceClient = profileServiceClient;
        this.renderServiceClient = renderServiceClient;
    }

    /** Renders this application's resume as a PDF, right now, from the job's current JD
     *  optimization and the candidate's current profile evidence. Every call re-renders —
     *  there is nothing to regenerate-by-calling-again, since nothing is persisted. */
    public byte[] renderResumePdf(String userId, String applicationId) {
        Application application = applicationService.requireOwned(userId, applicationId);

        JdOptimizationDto optimization = fetchOptimization(application.jobDescriptionId());
        List<EvidenceItem> evidence = fetchEvidence();
        PersonalInformationDto personalInfo = fetchPersonalInfo();

        ResumeRenderRequest request = assemble(personalInfo, evidence, optimization);
        RenderResponse response = callRenderService(request, applicationId);

        if (!"SUCCEEDED".equals(response.status()) || response.pdfBytes() == null || response.pdfBytes().length == 0) {
            log.warn("render-service did not produce a PDF for applicationId={}: {}",
                    applicationId, describeFailure(response));
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED);
        }
        return response.pdfBytes();
    }

    // ------------------------------------------------------------- assembly ----

    /** Deterministic assembly only — see class Javadoc. */
    private ResumeRenderRequest assemble(PersonalInformationDto personalInfo, List<EvidenceItem> evidence,
                                         JdOptimizationDto optimization) {
        if (personalInfo == null || !hasText(personalInfo.fullName()) || !hasText(personalInfo.email())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add your name and email to your profile before rendering a resume.");
        }

        Set<String> citedIds = optimization.citedEvidenceIds() == null
                ? Set.of() : Set.copyOf(optimization.citedEvidenceIds());
        Map<String, Integer> rankByEvidenceId = extractEmphasisRanks(optimization.optimisation());

        List<EvidenceItem> relevant = evidence.stream().filter(e -> citedIds.contains(e.evidenceId())).toList();
        if (relevant.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The JD optimization for this job description doesn't cite enough of your profile "
                            + "to render a resume. Confirm a JD optimization has been generated.");
        }

        List<RenderResumeSection> sections = new ArrayList<>();
        for (String type : SECTION_ORDER) {
            List<EvidenceItem> items = relevant.stream()
                    .filter(e -> type.equals(e.type()))
                    .sorted(Comparator.comparingInt(e -> rankByEvidenceId.getOrDefault(e.evidenceId(), Integer.MAX_VALUE)))
                    .toList();
            if (!items.isEmpty()) {
                sections.add(new RenderResumeSection(sectionHeading(type),
                        items.stream().map(ResumeRenderService::toSectionEntry).toList()));
            }
        }

        RenderDocumentHeader header = new RenderDocumentHeader(personalInfo.fullName(), personalInfo.email(),
                personalInfo.phone(), null, toHeaderLinks(personalInfo.links()));

        return new ResumeRenderRequest("1.0.0", "STANDARD", "PDF", header, null, sections,
                new RenderHints("A4", 1, "ARIAL", null));
    }

    private static String sectionHeading(String evidenceType) {
        return switch (evidenceType) {
            case "EXPERIENCE" -> "EXPERIENCE";
            case "EDUCATION" -> "EDUCATION";
            case "SKILL" -> "SKILLS";
            case "PROJECT" -> "PROJECTS";
            case "CERTIFICATION" -> "CERTIFICATIONS";
            case "ACHIEVEMENT" -> "ACHIEVEMENTS";
            default -> throw new IllegalStateException("Unknown evidence type: " + evidenceType);
        };
    }

    private static RenderSectionEntry toSectionEntry(EvidenceItem item) {
        List<RenderContentLeaf> bullets = hasText(item.description())
                ? List.of(new RenderContentLeaf(item.description(), List.of(item.evidenceId()), "VERBATIM_FROM_PROFILE"))
                : List.of();
        return new RenderSectionEntry(item.evidenceId(), item.title(), item.organisation(), null,
                item.startDate(), item.endDate(), bullets);
    }

    /** Reads the JD optimization's own {@code emphasis} ranking (evidenceId -> rank) straight
     *  out of its already-opaque {@code optimisation} map — the same map jd-service itself
     *  never interprets beyond persisting and republishing. Absent or malformed entries are
     *  simply not ranked, never an error: emphasis is a display-order hint, not a requirement. */
    @SuppressWarnings("unchecked")
    private static Map<String, Integer> extractEmphasisRanks(Map<String, Object> optimisation) {
        if (optimisation == null || !(optimisation.get("emphasis") instanceof List<?> emphasis)) {
            return Map.of();
        }
        Map<String, Integer> ranks = new HashMap<>();
        for (Object entryRaw : emphasis) {
            if (entryRaw instanceof Map<?, ?> entry
                    && entry.get("evidenceId") instanceof String evidenceId
                    && entry.get("rank") instanceof Number rank) {
                ranks.put(evidenceId, rank.intValue());
            }
        }
        return ranks;
    }

    private static List<RenderHeaderLink> toHeaderLinks(List<String> links) {
        if (links == null) {
            return List.of();
        }
        return links.stream().filter(ResumeRenderService::hasText).map(ResumeRenderService::toHeaderLink).toList();
    }

    private static RenderHeaderLink toHeaderLink(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        String label = lower.contains("linkedin.com") ? "LinkedIn"
                : lower.contains("github.com") ? "GitHub"
                : "Link";
        return new RenderHeaderLink(label, url);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // ------------------------------------------------------------- upstream calls ----

    private JdOptimizationDto fetchOptimization(String jobDescriptionId) {
        try {
            return jdServiceClient.getOptimization(jobDescriptionId);
        } catch (FeignException.NotFound ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Generate a JD optimization for this job description before rendering a resume.");
        } catch (FeignException ex) {
            log.warn("jd-service optimization call failed for jobDescriptionId={}: {}",
                    jobDescriptionId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private List<EvidenceItem> fetchEvidence() {
        try {
            return profileServiceClient.getEvidence();
        } catch (FeignException ex) {
            log.warn("profile-service evidence call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private PersonalInformationDto fetchPersonalInfo() {
        try {
            ProfileDto profile = profileServiceClient.getProfile();
            return profile == null ? null : profile.personalInformation();
        } catch (FeignException ex) {
            log.warn("profile-service personal-information call failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    /** The three failure modes this integration must tell apart (timeout/unavailable,
     *  validation failure, rendering failure): a connectivity problem or 5xx from render-service
     *  is "unavailable"; a 400 means render-service's own validation rejected what we assembled;
     *  neither is a "rendering failure" in the {@code RenderResponse.status == "FAILED"} sense,
     *  which is handled separately, back in {@link #renderResumePdf}, since render-service
     *  answered successfully there — it just couldn't produce a usable PDF. */
    private RenderResponse callRenderService(ResumeRenderRequest request, String applicationId) {
        try {
            return renderServiceClient.renderResume(request);
        } catch (FeignException.BadRequest ex) {
            log.warn("render-service rejected the assembled request for applicationId={}: {}",
                    applicationId, ex.getMessage());
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The resume could not be assembled into a document render-service accepts.");
        } catch (FeignException ex) {
            log.warn("render-service call failed for applicationId={}: {}", applicationId, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
    }

    private static String describeFailure(RenderResponse response) {
        if (response.errors() == null || response.errors().isEmpty()) {
            return "no error detail returned";
        }
        return response.errors().get(0).code() + ": " + response.errors().get(0).message();
    }
}
