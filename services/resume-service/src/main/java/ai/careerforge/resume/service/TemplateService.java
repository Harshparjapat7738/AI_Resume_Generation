package ai.careerforge.resume.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.repository.TemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Template catalogue reads plus the selection guard generation relies on. Ownership is
 * checked here so it applies uniformly however a caller reaches generation — see
 * {@link ai.careerforge.resume.domain.Template#isSelectableBy(String)}.
 */
@Service
public class TemplateService {

    /** Selected when generation doesn't specify a templateId — keeps the existing
     *  no-template-selected generation path working (backward compatible). */
    public static final String DEFAULT_TEMPLATE_ID = "classic";

    private final TemplateRepository templates;

    public TemplateService(TemplateRepository templates) {
        this.templates = templates;
    }

    public List<Template> list(TemplateType type) {
        return templates.findByTypeAndStatusOrderByNameAsc(type, TemplateStatus.ACTIVE);
    }

    /** 404s (never 403 — ADR-007 BOLA hardening) for a missing, disabled, or unauthorized
     *  template, exactly like every other ownership check in this codebase. */
    public Template get(String userId, String id) {
        Template template = templates.findById(id).orElseThrow(ApiException::notOwned);
        if (!template.isActive() || !template.isSelectableBy(userId)) {
            throw ApiException.notOwned();
        }
        return template;
    }

    /** Resolves the template to stamp on a generation. A blank id defaults to
     *  {@link #DEFAULT_TEMPLATE_ID} so callers that don't select a template yet keep working. */
    public Template resolveForGeneration(String userId, String templateId) {
        String effectiveId = (templateId == null || templateId.isBlank()) ? DEFAULT_TEMPLATE_ID : templateId;
        return get(userId, effectiveId);
    }
}
