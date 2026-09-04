package ai.careerforge.application.repository;

import ai.careerforge.application.domain.ApplicationStatusHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ApplicationStatusHistoryRepository extends MongoRepository<ApplicationStatusHistory, String> {

    List<ApplicationStatusHistory> findByApplicationIdAndUserIdOrderByChangedAtDesc(String applicationId, String userId);
}
