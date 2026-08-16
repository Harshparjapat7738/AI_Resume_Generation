package ai.careerforge.profile.repository;

import ai.careerforge.profile.domain.Template;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TemplateRepository extends MongoRepository<Template, String> {

    List<Template> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Template> findByIdAndUserId(String id, String userId);

    Optional<Template> findByUserIdAndIsDefaultTrue(String userId);

    long countByUserId(String userId);

    boolean existsByUserIdAndSha256(String userId, String sha256);
}
