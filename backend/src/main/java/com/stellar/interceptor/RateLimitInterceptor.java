package com.stellar.interceptor;

import com.stellar.common.BusinessException;
import com.stellar.common.ResultCode;
import com.stellar.common.annotation.RateLimit;
import com.stellar.interceptor.WebUtils;
import com.stellar.infra.RateLimitService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 单日限流拦截器：识别 {@link RateLimit} 注解，双档配额——游客按 IP 计数、
 * 登录用户按 userId 计数，超限抛 {@code BusinessException(TOO_MANY_REQUESTS)}，
 * 由全局异常处理器转 429 envelope。需注册在 {@code AuthInterceptor} 之后。
 *
 * @author stellar
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        if (rateLimit == null) {
            return true;
        }
        String subject;
        int daily;
        if (StpUtil.isLogin()) {
            // 登录身份按 userId 计数：换 IP 不重置配额，登录不再是免限流通道
            subject = "user:" + StpUtil.getLoginIdAsLong();
            daily = rateLimit.loginDaily();
        } else {
            subject = "ip:" + WebUtils.getClientIp(request);
            daily = rateLimit.daily();
        }
        int result = rateLimitService.tryIncr(subject, daily);
        if (result < 0) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }
}
