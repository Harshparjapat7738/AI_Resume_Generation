package ai.careerforge.auth.repository;

import ai.careerforge.auth.domain.OAuthAccount;
import ai.careerforge.auth.domain.OAuthProvider;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OAuthAccountRepository extends MongoRepository<OAuthAccount, String> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
