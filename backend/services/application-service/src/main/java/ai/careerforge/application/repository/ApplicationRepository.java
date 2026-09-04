package ai.careerforge.application.repository;

import ai.careerforge.application.domain.Application;
import ai.careerforge.application.domain.ApplicationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ApplicationRepository extends MongoRepository<Application, String> {

    Optional<Application> findByIdAndUserId(String id, String userId);

    Page<Application> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Application> findByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, ApplicationStatus status, Pageable pageable);
}
