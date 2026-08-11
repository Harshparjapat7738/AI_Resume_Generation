package ai.careerforge.document.repository;

import ai.careerforge.document.domain.CustomTemplateAsset;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomTemplateAssetRepository extends MongoRepository<CustomTemplateAsset, String> {

    Optional<CustomTemplateAsset> findByIdAndUserId(String id, String userId);
}
