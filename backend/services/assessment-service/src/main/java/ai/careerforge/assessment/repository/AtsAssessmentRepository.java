package ai.careerforge.assessment.repository;

import ai.careerforge.assessment.domain.AtsAssessment;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AtsAssessmentRepository extends MongoRepository<AtsAssessment, String> {

    Optional<AtsAssessment> findByJdOptimizationIdAndUserId(String jdOptimizationId, String userId);
}
