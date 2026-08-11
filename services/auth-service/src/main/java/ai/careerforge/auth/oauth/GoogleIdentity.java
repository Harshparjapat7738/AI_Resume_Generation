package ai.careerforge.auth.oauth;

/** The verified identity Google's ID token vouches for — never trusted until the token's
 *  signature, issuer, audience and expiry have all been checked. */
record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
