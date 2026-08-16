package ai.careerforge.profile.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** Explicit index creation — see docs/DATABASE.md &sect;2 (auto-index-creation is off). */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps("profiles")
                .ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique());
        // Many templates per user (unlike the one-per-user "profiles" index above) — supports
        // both the list-by-owner query and the duplicate-upload (userId+sha256) check without a
        // collection scan (ADR-034).
        mongoTemplate.indexOps("templates")
                .ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        mongoTemplate.indexOps("templates")
                .ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("sha256", Sort.Direction.ASC));
        log.info("profile-service Mongo indexes ensured");
    }
}
