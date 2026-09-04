package ai.careerforge.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RFC 7636 Proof Key for Code Exchange — the {@code code_verifier} never leaves this
 * service (docs/EXTERNAL_APIS.md "Google OAuth 2.0": "Authorization Code with PKCE,
 * executed entirely server-side"). Only the derived {@code code_challenge} is sent to
 * Google in the authorize request; the verifier is presented later, at token exchange,
 * proving this service — not an attacker who intercepted the authorization code — started
 * the flow.
 */
final class PkceGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceGenerator() {
    }

    record Pair(String verifier, String challenge) {
    }

    static Pair generate() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Pair(verifier, challengeFor(verifier));
    }

    /** S256: base64url(sha256(verifier)) — the only method Google's endpoint accepts. */
    private static String challengeFor(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
