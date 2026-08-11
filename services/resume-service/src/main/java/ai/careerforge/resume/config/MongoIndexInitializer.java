package ai.careerforge.resume.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
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
        mongoTemplate.indexOps("resume_generations").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("startedAt", -1)));

        mongoTemplate.indexOps("resume_versions").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("createdAt", -1)));

        mongoTemplate.indexOps("templates").ensureIndex(
                new CompoundIndexDefinition(new Document("type", 1).append("status", 1)));
        mongoTemplate.indexOps("templates").ensureIndex(
                new CompoundIndexDefinition(new Document("source", 1).append("ownerUserId", 1)));
        mongoTemplate.indexOps("templates").ensureIndex(
                new CompoundIndexDefinition(new Document("ownerUserId", 1).append("type", 1).append("status", 1).append("createdAt", -1)));

        log.info("resume-service Mongo indexes ensured");
    }
}
