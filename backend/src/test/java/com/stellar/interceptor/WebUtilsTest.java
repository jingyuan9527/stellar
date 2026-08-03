package com.stellar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link WebUtils} 单测：IP 解析头优先级（X-Forwarded-For > X-Real-IP > remoteAddr）、
 * 空头逐级降级、request 为空/无请求上下文返回 unknown、上下文解析异常兜底。
 */
@ExtendWith(MockitoExtension.class)
class WebUtilsTest {

    @Mock
    HttpServletRequest request;

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getClientIp_null请求_unknown() {
        assertEquals("unknown", WebUtils.getClientIp((HttpServletRequest) null));
    }

    @Test
    void getClientIp_取XForwardedFor首段() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        assertEquals("1.2.3.4", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_无XFF_取XRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("9.9.9.9");
        assertEquals("9.9.9.9", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_无头_取remoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn("  ");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_全部为空_unknown() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(" ");
        assertEquals("unknown", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_无请求上下文_unknown() {
        assertEquals("unknown", WebUtils.getClientIp());
    }

    @Test
    void getClientIp_有上下文_透传解析() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("8.8.8.8", WebUtils.getClientIp());
    }

    @Test
    void getClientIp_上下文异常_unknown() {
        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes)
                    .thenThrow(new IllegalStateException("broken"));
            assertEquals("unknown", WebUtils.getClientIp());
        }
    }
}
