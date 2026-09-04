package ai.careerforge.assessment.service;

import ai.careerforge.assessment.client.ClientDtos.EvidenceItem;
import ai.careerforge.assessment.client.ClientDtos.JdOptimizationDto;
import ai.careerforge.assessment.client.ClientDtos.PersonalInformationDto;
import ai.careerforge.assessment.domain.AtsCheckResult;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic ATS *structural* scoring (ADR-040, reviving what ADR-033 deferred and ADR-036
 * left deferred). Scores the same cited-evidence-per-section content
 * {@code ResumeRenderService.assemble()} builds for {@code render-service} in
 * application-service — never a rendered PDF/DOCX, so this is available whether or not that
 * later render call succeeds. Every sub-check returns a fractional {@code [0.0, 1.0]} pass
 * ratio, never a plain boolean (ADR-008) — a document that fails one check inside one weighted
 * category lands just below a perfect score rather than jumping straight to it.
 *
 * <p>Deliberately does not read {@code render-service}'s output, an uploaded "My Templates"
 * file (ADR-034 — a stored-and-returned-as-uploaded file has no structure to check), or a
 * generated PDF/DOCX of any kind — the whole point of scoping this to pre-render content is that
 * it never depends on the render step.
 */
@Component
public class AtsScoringEngine {

    /** Mirrors {@code ResumeRenderService.SECTION_ORDER} exactly — the same six evidence types,
     *  same standard section shape, so "does this resume have a Skills section" means the same
     *  thing here as it will on the actual rendered document. */
    private static final List<String> SECTION_ORDER =
            List.of("EXPERIENCE", "EDUCATION", "SKILL", "PROJECT", "CERTIFICATION", "ACHIEVEMENT");

    public record Result(double atsScore, List<AtsCheckResult> checks) {
    }

    public Result score(JdOptimizationDto optimization, PersonalInformationDto personalInfo,
                        List<EvidenceItem> evidence) {
        Set<String> citedIds = optimization.citedEvidenceIds() == null
                ? Set.of() : Set.copyOf(optimization.citedEvidenceIds());
        List<EvidenceItem> relevant = evidence == null ? List.of()
                : evidence.stream().filter(e -> citedIds.contains(e.evidenceId())).toList();

        List<AtsCheckResult> checks = List.of(
                contactInformation(personalInfo),
                experienceSection(relevant),
                skillsSection(relevant),
                sectionBreadth(relevant),
                bulletedContent(relevant),
                parseableDates(relevant));

        double atsScore = Math.round(checks.stream().mapToDouble(c -> c.weight() * c.passRatio()).sum() * 1000.0) / 10.0;
        return new Result(atsScore, checks);
    }

    private AtsCheckResult contactInformation(PersonalInformationDto personalInfo) {
        double ratio = 0.0;
        if (personalInfo != null) {
            if (hasText(personalInfo.fullName())) ratio += 0.5;
            if (hasText(personalInfo.email())) ratio += 0.5;
        }
        return new AtsCheckResult("contactInformation", "Contact information present", ratio, 0.15);
    }

    private AtsCheckResult experienceSection(List<EvidenceItem> relevant) {
        boolean hasExperience = relevant.stream().anyMatch(e -> "EXPERIENCE".equals(e.type()));
        return new AtsCheckResult("experienceSection", "Experience section present",
                hasExperience ? 1.0 : 0.0, 0.30);
    }

    private AtsCheckResult skillsSection(List<EvidenceItem> relevant) {
        boolean hasSkills = relevant.stream().anyMatch(e -> "SKILL".equals(e.type()));
        return new AtsCheckResult("skillsSection", "Skills section present", hasSkills ? 1.0 : 0.0, 0.15);
    }

    private AtsCheckResult sectionBreadth(List<EvidenceItem> relevant) {
        long present = SECTION_ORDER.stream()
                .filter(type -> relevant.stream().anyMatch(e -> type.equals(e.type())))
                .count();
        double ratio = (double) present / SECTION_ORDER.size();
        return new AtsCheckResult("sectionBreadth", "Standard sections covered", ratio, 0.15);
    }

    /** Only Experience/Project entries are checked — the same two evidence types
     *  {@code ResumeRenderService.shouldShowAsBullet} treats as genuine prose; Skill's
     *  "description" is really a proficiency level there too, so it is excluded here for the
     *  same reason. */
    private AtsCheckResult bulletedContent(List<EvidenceItem> relevant) {
        List<EvidenceItem> checkable = relevant.stream()
                .filter(e -> "EXPERIENCE".equals(e.type()) || "PROJECT".equals(e.type()))
                .toList();
        if (checkable.isEmpty()) {
            return new AtsCheckResult("bulletedContent", "Entries have real content", 0.0, 0.15);
        }
        long withContent = checkable.stream().filter(this::hasRealContent).count();
        double ratio = (double) withContent / checkable.size();
        return new AtsCheckResult("bulletedContent", "Entries have real content", ratio, 0.15);
    }

    private boolean hasRealContent(EvidenceItem item) {
        if (item.bullets() != null && item.bullets().stream().anyMatch(this::hasText)) {
            return true;
        }
        return hasText(item.description());
    }

    /** Only Experience/Education entries carry a tenure-relevant start date worth checking —
     *  Skill/Project/Certification/Achievement dates aren't part of the standard ATS
     *  date-parsing expectation {@code ResumeRenderService.formatDisplayDate}'s own Javadoc
     *  describes. */
    private AtsCheckResult parseableDates(List<EvidenceItem> relevant) {
        List<EvidenceItem> checkable = relevant.stream()
                .filter(e -> "EXPERIENCE".equals(e.type()) || "EDUCATION".equals(e.type()))
                .toList();
        if (checkable.isEmpty()) {
            return new AtsCheckResult("parseableDates", "Dates are machine-parseable", 0.0, 0.10);
        }
        long parseable = checkable.stream().filter(e -> isParseableYearMonth(e.startDate())).count();
        double ratio = (double) parseable / checkable.size();
        return new AtsCheckResult("parseableDates", "Dates are machine-parseable", ratio, 0.10);
    }

    private boolean isParseableYearMonth(String value) {
        if (!hasText(value)) return false;
        try {
            YearMonth.parse(value.trim());
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
