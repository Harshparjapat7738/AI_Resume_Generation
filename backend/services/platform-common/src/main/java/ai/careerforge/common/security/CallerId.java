package ai.careerforge.common.security;

/**
 * Marks a controller method parameter that should be populated with the authenticated
 * caller's user ID, as verified by the gateway.
 *
 * <pre>{@code
 * @GetMapping("/api/profile")
 * ProfileResponse get(@CallerId String userId) { ... }
 * }</pre>
 */
@java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Documented
public @interface CallerId {
}
