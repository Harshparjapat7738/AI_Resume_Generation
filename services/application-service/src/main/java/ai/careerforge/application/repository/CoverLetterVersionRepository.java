package ai.careerforge.application.repository;

import ai.careerforge.application.domain.CoverLetterVersion;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CoverLetterVersionRepository extends MongoRepository<CoverLetterVersion, String> {

    Optional<CoverLetterVersion> findByIdAndUserId(String id, String userId);

    Optional<CoverLetterVersion> findTopByApplicationIdAndUserIdOrderByVersionDesc(
            String applicationId, String userId);
}
