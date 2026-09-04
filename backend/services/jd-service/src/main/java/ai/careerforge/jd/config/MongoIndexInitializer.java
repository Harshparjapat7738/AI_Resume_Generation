package ai.careerforge.jd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.bson.Document;
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
        mongoTemplate.indexOps("job_descriptions").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("createdAt", -1)));

        mongoTemplate.indexOps("jd_versions").ensureIndex(
                new CompoundIndexDefinition(new Document("jobDescriptionId", 1).append("version", 1)).unique());

        mongoTemplate.indexOps("jd_analyses").ensureIndex(
                new Index().on("jdVersionId", Sort.Direction.ASC).unique());

        // One current optimization per JD version (see JdOptimization's own Javadoc), and
        // every read is owner-scoped.
        mongoTemplate.indexOps("jd_optimizations").ensureIndex(
                new Index().on("jdVersionId", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("jd_optimizations").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("createdAt", -1)));

        log.info("jd-service Mongo indexes ensured");
    }
}
