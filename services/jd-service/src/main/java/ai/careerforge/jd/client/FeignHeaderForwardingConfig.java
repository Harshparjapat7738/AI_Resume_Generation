package ai.careerforge.jd.client;

import ai.careerforge.common.security.CallerIdArgumentResolver;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's identity from the inbound request onto outgoing Feign calls.
 *
 * <p>jd-service calls profile-service directly (Eureka, not through the
 * gateway), so profile-service never sees the gateway's own header-stripping/re-setting step.
 * The header is trustworthy here for the same reason it is trustworthy at the gateway: these
 * services are reachable only on the internal network (docs/CODEBASE.md &sect;4).
 */
public class FeignHeaderForwardingConfig {

    @Bean
    public RequestInterceptor forwardCallerId() {
        return template -> {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                String userId = servletAttributes.getRequest().getHeader(CallerIdArgumentResolver.USER_ID_HEADER);
                if (userId != null) {
                    template.header(CallerIdArgumentResolver.USER_ID_HEADER, userId);
                }
            }
        };
    }
}
