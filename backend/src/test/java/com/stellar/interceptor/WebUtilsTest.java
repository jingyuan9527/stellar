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
 * {@link WebUtils} 单测：可信代理白名单下的 IP 解析——
 * 可信来源（remoteAddr 命中白名单）按 X-Forwarded-For &gt; X-Real-IP &gt; remoteAddr 解析；
 * 不可信直连忽略代理头（防伪造绕过限流）、空头逐级降级、异常兜底。
 */
@ExtendWith(MockitoExtension.class)
class WebUtilsTest {

    @Mock
    HttpServletRequest request;

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        // 恢复默认白名单，避免影响其他测试类
        WebUtils.configureTrustedProxies("127.0.0.1,::1,0:0:0:0:0:0:0:1");
    }

    @Test
    void getClientIp_null请求_unknown() {
        assertEquals("unknown", WebUtils.getClientIp((HttpServletRequest) null));
    }

    @Test
    void getClientIp_可信代理_取XForwardedFor首段() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        assertEquals("1.2.3.4", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_不可信直连_忽略伪造XFF() {
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        // 不可信来源不会读取代理头，lenient 仅表达"客户端可伪造携带"
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6");
        assertEquals("8.8.8.8", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_可信代理CIDR段_采信代理头() {
        WebUtils.configureTrustedProxies("10.0.0.0/8");
        when(request.getRemoteAddr()).thenReturn("10.1.2.3");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9");
        assertEquals("9.9.9.9", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_无XFF_取XRealIp() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
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
    void getClientIp_remoteAddr为空_unknown() {
        // remoteAddr 为空白视为不可信，直接返回 unknown（代理头不参与）
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(" ");
        assertEquals("unknown", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_无请求上下文_unknown() {
        assertEquals("unknown", WebUtils.getClientIp());
    }

    @Test
    void getClientIp_有上下文_透传解析() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
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
