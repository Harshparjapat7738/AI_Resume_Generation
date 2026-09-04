package ai.careerforge.jd.repository;

import ai.careerforge.jd.domain.JdAnalysis;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JdAnalysisRepository extends MongoRepository<JdAnalysis, String> {

    Optional<JdAnalysis> findByJdVersionId(String jdVersionId);
}
