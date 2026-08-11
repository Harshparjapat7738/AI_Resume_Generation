package ai.careerforge.resume.repository;

import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TemplateRepository extends MongoRepository<Template, String> {

    List<Template> findByTypeAndStatusOrderByNameAsc(TemplateType type, TemplateStatus status);

    /** A user's own custom uploads for one type — never returns another user's rows; see
     *  {@code TemplateService#list} for why this and the built-in query above are combined
     *  rather than one query trying to express both conditions. */
    List<Template> findByOwnerUserIdAndTypeAndStatusOrderByCreatedAtDesc(
            String ownerUserId, TemplateType type, TemplateStatus status);
}
