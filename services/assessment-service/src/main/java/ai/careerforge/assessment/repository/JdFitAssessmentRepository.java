package ai.careerforge.assessment.repository;

import ai.careerforge.assessment.domain.JdFitAssessment;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JdFitAssessmentRepository extends MongoRepository<JdFitAssessment, String> {

    Optional<JdFitAssessment> findByResumeVersionIdAndUserId(String resumeVersionId, String userId);
}
