package ai.careerforge.resume.repository;

import ai.careerforge.resume.domain.ResumeGeneration;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResumeGenerationRepository extends MongoRepository<ResumeGeneration, String> {
}
