package ai.careerforge.jd.repository;

import ai.careerforge.jd.domain.JdOptimization;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JdOptimizationRepository extends MongoRepository<JdOptimization, String> {

    /** One current optimization per JD version — see {@code JdOptimization}'s own Javadoc. */
    Optional<JdOptimization> findByJdVersionId(String jdVersionId);

    /** Every read is scoped by owner: another user's optimization is simply not found, which is
     *  what turns into the platform's 404-never-403 response (ADR-007). */
    Optional<JdOptimization> findByIdAndUserId(String id, String userId);
}
