package ai.careerforge.auth.config;

import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates indexes explicitly at startup rather than relying on
 * {@code auto-index-creation} (off — docs/DATABASE.md &sect;2), so index changes stay a
 * reviewable, auditable diff instead of a side effect of the entity annotations.
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations users = mongoTemplate.indexOps("users");
        users.ensureIndex(new Index().on("email", org.springframework.data.domain.Sort.Direction.ASC).unique());

        IndexOperations refreshTokens = mongoTemplate.indexOps("refresh_tokens");
        refreshTokens.ensureIndex(
                new Index().on("tokenHash", org.springframework.data.domain.Sort.Direction.ASC).unique());
        refreshTokens.ensureIndex(new Index().on("familyId", org.springframework.data.domain.Sort.Direction.ASC));
        refreshTokens.ensureIndex(new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                .expire(0, TimeUnit.SECONDS));

        IndexOperations oauthAccounts = mongoTemplate.indexOps("oauth_accounts");
        oauthAccounts.ensureIndex(
                new CompoundIndexDefinition(new Document("provider", 1).append("providerUserId", 1)).unique());
        oauthAccounts.ensureIndex(new Index().on("userId", org.springframework.data.domain.Sort.Direction.ASC));

        mongoTemplate.indexOps("security_events").ensureIndex(
                new CompoundIndexDefinition(new Document("userId", 1).append("occurredAt", -1)));

        log.info("auth-service Mongo indexes ensured");
    }
}
