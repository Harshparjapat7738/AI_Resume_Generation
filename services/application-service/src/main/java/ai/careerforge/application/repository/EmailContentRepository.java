package ai.careerforge.application.repository;

import ai.careerforge.application.domain.EmailContent;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailContentRepository extends MongoRepository<EmailContent, String> {

    Optional<EmailContent> findByIdAndUserId(String id, String userId);

    Optional<EmailContent> findTopByApplicationIdAndUserIdOrderByVersionDesc(String applicationId, String userId);
}
