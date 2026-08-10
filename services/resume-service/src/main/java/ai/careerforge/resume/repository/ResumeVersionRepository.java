package ai.careerforge.resume.repository;

import ai.careerforge.resume.domain.ResumeVersion;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResumeVersionRepository extends MongoRepository<ResumeVersion, String> {

    Optional<ResumeVersion> findByIdAndUserId(String id, String userId);
}
