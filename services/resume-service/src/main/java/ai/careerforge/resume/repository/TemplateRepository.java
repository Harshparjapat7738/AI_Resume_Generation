package ai.careerforge.resume.repository;

import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TemplateRepository extends MongoRepository<Template, String> {

    List<Template> findByTypeAndStatusOrderByNameAsc(TemplateType type, TemplateStatus status);
}
