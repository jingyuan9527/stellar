package com.stellar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link RequestLogInterceptor} 单测：preHandle 生成 8 位 traceId 写入 MDC 并记录开始时间；
 * afterCompletion 正常/4xx/5xx 均清理 MDC，含/不含开始时间、带/不带 queryString 分支。
 */
@ExtendWith(MockitoExtension.class)
class RequestLogInterceptorTest {

    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;

    @Test
    void preHandle_写入traceId与开始时间() {
        RequestLogInterceptor interceptor = new RequestLogInterceptor();
        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        String traceId = MDC.get("traceId");
        assertNotNull(traceId);
        assertEquals(8, traceId.length());
        assertFalse(traceId.contains("-"));
        verify(request).setAttribute(eq("requestStartTime"), anyLong());
        MDC.clear();
    }

    @Test
    void afterCompletion_正常请求_清理MDC() {
        RequestLogInterceptor interceptor = new RequestLogInterceptor();
        interceptor.preHandle(request, response, new Object());
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/home");
        when(request.getMethod()).thenReturn("GET");
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(MDC.get("traceId"));
    }

    @Test
    void afterCompletion_带queryString与4xx() {
        RequestLogInterceptor interceptor = new RequestLogInterceptor();
        when(request.getAttribute("requestStartTime")).thenReturn(System.currentTimeMillis() - 123L);
        when(request.getQueryString()).thenReturn("a=1");
        when(request.getRequestURI()).thenReturn("/home");
        when(request.getMethod()).thenReturn("GET");
        when(response.getStatus()).thenReturn(404);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(MDC.get("traceId"));
    }

    @Test
    void afterCompletion_无开始时间_耗时降级() {
        RequestLogInterceptor interceptor = new RequestLogInterceptor();
        when(request.getAttribute("requestStartTime")).thenReturn(null);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/error");
        when(request.getMethod()).thenReturn("POST");
        when(response.getStatus()).thenReturn(500);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(MDC.get("traceId"));
    }
}
