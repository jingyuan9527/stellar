package com.stellar.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.common.ResultCode;
import com.stellar.common.annotation.RateLimit;
import com.stellar.infra.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RateLimitInterceptor} 单测：隔离 {@link RateLimitService} 与 {@link StpUtil} 静态调用，
 * 验证注解识别、游客按 IP 计数、登录用户按 userId 计数、超限抛 429（BusinessException(TOO_MANY_REQUESTS)）。
 * IP 解析复用真实 {@link WebUtils#getClientIp(HttpServletRequest)}（mock 可信代理 remoteAddr + XFF）。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HandlerMethod handlerMethod;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // 必须在 @Mock 注入后构造，否则构造函数拿到的 rateLimitService 为 null
        interceptor = new RateLimitInterceptor(rateLimitService);
    }

    /** mock 可信反代来源（remoteAddr=回环），XFF 头才会被采信 */
    private void stubTrustedProxyWithXff(String xff) {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
    }

    @Test
    void preHandle_非HandlerMethod_直接放行() {
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void preHandle_无注解_放行且不计数() {
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(null);
        doReturn(Object.class).when(handlerMethod).getBeanType();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            verify(rateLimitService, never()).tryIncr(anyString(), anyInt());
        }
    }

    @Test
    void preHandle_已登录_按userId计数() {
        RateLimit rl = mock(RateLimit.class);
        when(rl.loginDaily()).thenReturn(50);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        when(rateLimitService.tryIncr("user:42", 50)).thenReturn(1);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
        }
    }

    @Test
    void preHandle_已登录_超限同样429() {
        RateLimit rl = mock(RateLimit.class);
        when(rl.loginDaily()).thenReturn(-1); // 用默认用户档
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        when(rateLimitService.tryIncr(eq("user:42"), anyInt())).thenReturn(-1);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod));
            assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        }
    }

    @Test
    void preHandle_游客未超限_放行() {
        RateLimit rl = mock(RateLimit.class);
        when(rl.daily()).thenReturn(5);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        stubTrustedProxyWithXff("9.9.9.9");
        when(rateLimitService.tryIncr("ip:9.9.9.9", 5)).thenReturn(3);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
        }
    }

    @Test
    void preHandle_游客超限_抛429() {
        RateLimit rl = mock(RateLimit.class);
        when(rl.daily()).thenReturn(5);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        stubTrustedProxyWithXff("9.9.9.9");
        when(rateLimitService.tryIncr("ip:9.9.9.9", 5)).thenReturn(-1);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod));
            assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        }
    }
}
