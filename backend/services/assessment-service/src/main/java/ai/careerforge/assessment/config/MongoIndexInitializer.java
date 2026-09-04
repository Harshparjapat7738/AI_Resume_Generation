package ai.careerforge.assessment.config;

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
        // ADR-033: keyed on the JD optimization, not a resume version. The old
        // `resumeVersionId + userId` index and the whole `ats_assessments` collection are legacy
        // — see docs/DATABASE.md for the documented drop procedure.
        mongoTemplate.indexOps("jd_fit_assessments").ensureIndex(
                new CompoundIndexDefinition(new Document("jdOptimizationId", 1).append("userId", 1)).unique());

        // ADR-040: ATS structural scoring revived, deliberately in a new collection
        // (`ats_structural_assessments`), not the legacy, dead `ats_assessments` above — same
        // key shape as jd_fit_assessments, for the same reason (one current optimization per JD
        // version).
        mongoTemplate.indexOps("ats_structural_assessments").ensureIndex(
                new CompoundIndexDefinition(new Document("jdOptimizationId", 1).append("userId", 1)).unique());

        log.info("assessment-service Mongo indexes ensured");
    }
}
