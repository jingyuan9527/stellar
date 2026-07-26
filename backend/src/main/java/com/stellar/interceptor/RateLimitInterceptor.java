package com.stellar.interceptor;

import com.stellar.common.BusinessException;
import com.stellar.common.ResultCode;
import com.stellar.common.annotation.RateLimit;
import com.stellar.service.RateLimitService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * IP 单日限流拦截器：识别 {@link RateLimit} 注解，按 IP 计数，超限抛
 * {@code BusinessException(TOO_MANY_REQUESTS)}，由全局异常处理器转 429 envelope。
 *
 * <p>登录用户跳过限流（仅对游客计数）。需注册在 {@code AuthInterceptor} 之后（放行后计数）。
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
        if (StpUtil.isLogin()) {
            return true;
        }
        String ip = getClientIp(request);
        int result = rateLimitService.tryIncr(ip, rateLimit.daily());
        if (result < 0) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    /**
     * 解析客户端真实 IP，穿透代理头。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
