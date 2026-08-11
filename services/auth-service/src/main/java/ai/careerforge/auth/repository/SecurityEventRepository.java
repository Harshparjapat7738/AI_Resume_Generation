package ai.careerforge.auth.repository;

import ai.careerforge.auth.domain.SecurityEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SecurityEventRepository extends MongoRepository<SecurityEvent, String> {
}
