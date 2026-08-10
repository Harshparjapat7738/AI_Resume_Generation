package ai.careerforge.resume.config;

import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateSource;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.repository.TemplateRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the built-in template catalogue (docs/DATABASE.md &sect;3 "templates"). Idempotent —
 * {@code save} on a {@code Template} whose {@code id} already exists in Mongo overwrites the
 * document rather than duplicating it, so this can run on every startup.
 *
 * <p>All three are single-column, ATS-safe layouts. That's a deliberate constraint, not a
 * missing feature: document-service has no renderer yet (ARCHITECTURE_DECISIONS.md ADR-016),
 * so nothing here can honestly claim a multi-column layout parses cleanly through ATS
 * software until there is a real renderer to verify it against.
 */
@Component
public class TemplateSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateSeeder.class);

    private final TemplateRepository templates;

    public TemplateSeeder(TemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Template> builtIns = List.of(
                new Template(
                        "classic",
                        "Classic",
                        "Centered header, underlined section headings, single column. The safest "
                                + "default for traditional industries and ATS parsers alike.",
                        "classic",
                        TemplateType.RESUME,
                        "1",
                        TemplateStatus.ACTIVE,
                        TemplateSource.BUILT_IN,
                        null,
                        List.of("PDF", "DOCX"),
                        true),
                new Template(
                        "modern-ats",
                        "Modern ATS",
                        "Left-aligned header, sans-serif type, a single accent rule under each "
                                + "section heading. Built for readability by both people and parsers.",
                        "modern-ats",
                        TemplateType.RESUME,
                        "1",
                        TemplateStatus.ACTIVE,
                        TemplateSource.BUILT_IN,
                        null,
                        List.of("PDF", "DOCX"),
                        true),
                new Template(
                        "professional",
                        "Professional",
                        "A restrained color band behind the name and title, otherwise the same "
                                + "single-column body as Classic. A little more visual presence "
                                + "without touching ATS-safe structure.",
                        "professional",
                        TemplateType.RESUME,
                        "1",
                        TemplateStatus.ACTIVE,
                        TemplateSource.BUILT_IN,
                        null,
                        List.of("PDF", "DOCX"),
                        true));

        templates.saveAll(builtIns);
        log.info("resume-service built-in template catalogue seeded ({} templates)", builtIns.size());
    }
}
