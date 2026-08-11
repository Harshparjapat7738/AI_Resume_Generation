package ai.careerforge.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PkceGeneratorTest {

    @Test
    void challengeIsTheBase64UrlSha256OfTheVerifier() throws Exception {
        PkceGenerator.Pair pair = PkceGenerator.generate();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedHash = digest.digest(pair.verifier().getBytes(StandardCharsets.US_ASCII));
        String expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedHash);

        assertThat(pair.challenge()).isEqualTo(expectedChallenge);
    }

    @Test
    void verifierIsUrlSafeAndUnpadded() {
        PkceGenerator.Pair pair = PkceGenerator.generate();

        assertThat(pair.verifier()).doesNotContain("+", "/", "=");
        assertThat(pair.verifier().length()).isGreaterThanOrEqualTo(43); // RFC 7636 minimum
    }

    @Test
    void everyCallProducesADifferentVerifier() {
        PkceGenerator.Pair first = PkceGenerator.generate();
        PkceGenerator.Pair second = PkceGenerator.generate();

        assertThat(first.verifier()).isNotEqualTo(second.verifier());
    }
}
