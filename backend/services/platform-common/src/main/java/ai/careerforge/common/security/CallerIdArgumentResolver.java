package ai.careerforge.common.security;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CallerId} parameters from the {@code X-User-Id} header set by the gateway
 * after JWT verification.
 *
 * <p><strong>This is identity, not authorisation.</strong> Services must still check that the
 * targeted document's {@code userId} equals this value before reading or writing it.
 *
 * <p>The header is only trustworthy because the gateway strips any client-supplied value and
 * internal services are not exposed outside the Docker/private network.
 */
public class CallerIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CallerId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return userId;
    }
}
