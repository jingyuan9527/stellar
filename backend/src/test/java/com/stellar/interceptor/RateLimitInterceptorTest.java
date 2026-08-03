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
 * 验证注解识别、登录跳过、游客计数、超限抛 429（BusinessException(TOO_MANY_REQUESTS)）的流程。
 * IP 解析复用真实 {@link WebUtils#getClientIp(HttpServletRequest)}（读取 mock 请求的代理头）。
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
    void preHandle_已登录_跳过限流不计数() {
        // 已登录时即使带 @RateLimit 也直接放行，不触发计数（daily 不会被读取）
        RateLimit rl = mock(RateLimit.class);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            verify(rateLimitService, never()).tryIncr(anyString(), anyInt());
        }
    }

    @Test
    void preHandle_游客未超限_放行() {
        RateLimit rl = mock(RateLimit.class);
        when(rl.daily()).thenReturn(5);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rl);
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9");
        when(rateLimitService.tryIncr("9.9.9.9", 5)).thenReturn(3);
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
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9");
        when(rateLimitService.tryIncr("9.9.9.9", 5)).thenReturn(-1);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod));
            assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        }
    }
}
