package com.stellar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求日志拦截器：为每个请求生成 traceId 放入 MDC（logback 格式自动输出），
 * 记录请求方法/URI/HTTP状态/耗时。注册在所有拦截器之前，确保 traceId 贯穿全链路。
 *
 * @author stellar
 */
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String START_TIME = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        try {
            Object startAttr = request.getAttribute(START_TIME);
            long elapsed = startAttr != null ? System.currentTimeMillis() - (long) startAttr : -1;
            int status = response.getStatus();
            String qs = request.getQueryString();
            String uri = qs != null ? request.getRequestURI() + "?" + qs : request.getRequestURI();
            if (status >= 400) {
                log.warn("请求完成 {} {} status={} 耗时={}ms", request.getMethod(), uri, status, elapsed);
            } else {
                log.info("请求完成 {} {} status={} 耗时={}ms", request.getMethod(), uri, status, elapsed);
            }
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
