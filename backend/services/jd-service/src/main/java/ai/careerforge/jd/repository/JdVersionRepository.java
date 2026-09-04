package ai.careerforge.jd.repository;

import ai.careerforge.jd.domain.JdVersion;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JdVersionRepository extends MongoRepository<JdVersion, String> {

    Optional<JdVersion> findByJobDescriptionIdAndVersion(String jobDescriptionId, int version);
}
