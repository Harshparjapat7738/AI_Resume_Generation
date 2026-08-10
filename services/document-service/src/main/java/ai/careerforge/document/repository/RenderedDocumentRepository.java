package ai.careerforge.document.repository;

import ai.careerforge.document.domain.DocumentFormat;
import ai.careerforge.document.domain.RenderedDocument;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RenderedDocumentRepository extends MongoRepository<RenderedDocument, String> {

    Optional<RenderedDocument> findByResumeVersionIdAndFormatAndUserId(
            String resumeVersionId, DocumentFormat format, String userId);

    Optional<RenderedDocument> findByIdAndUserId(String id, String userId);
}
