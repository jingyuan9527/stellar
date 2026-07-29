package com.stellar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 层通用工具：当前仅封装客户端真实 IP 解析（穿透代理头）。
 * <p>解析优先级：X-Forwarded-For 首段 &gt; X-Real-IP &gt; 远端地址；无请求上下文（如异步线程）返回 {@code unknown}。
 * 拦截器与 Service 共用，避免 X-Forwarded-For 解析逻辑散落多处（见 RateLimitInterceptor / AiChatService）。
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 从请求解析客户端 IP。request 为 null 时返回 {@code unknown}。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            ip = ip.split(",")[0].trim();
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    /**
     * 从当前请求上下文解析客户端 IP（供 Service 层无 HttpServletRequest 入参处使用）。
     * 异步线程中请求上下文可能已失效，此时返回 {@code unknown}，调用方应降级处理。
     */
    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            return getClientIp(attributes.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }
}
