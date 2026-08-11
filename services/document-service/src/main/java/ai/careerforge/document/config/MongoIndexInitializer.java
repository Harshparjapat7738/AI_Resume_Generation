package ai.careerforge.document.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** Explicit index creation — see docs/DATABASE.md &sect;4 (auto-index-creation is off). */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps("rendered_documents").ensureIndex(
                new CompoundIndexDefinition(new Document("resumeVersionId", 1).append("format", 1))
                        .unique());

        mongoTemplate.indexOps("rendered_documents").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("renderedAt", -1)));

        mongoTemplate.indexOps("rendered_documents").ensureIndex(new Index("objectKey", Direction.ASC).unique());

        mongoTemplate.indexOps("custom_template_assets").ensureIndex(new Index("userId", Direction.ASC));
        mongoTemplate.indexOps("custom_template_assets").ensureIndex(new Index("objectKey", Direction.ASC).unique());

        log.info("document-service Mongo indexes ensured");
    }
}
