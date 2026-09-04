package ai.careerforge.jd.repository;

import ai.careerforge.jd.domain.JobDescription;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobDescriptionRepository extends MongoRepository<JobDescription, String> {

    Optional<JobDescription> findByIdAndUserId(String id, String userId);

    Page<JobDescription> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
