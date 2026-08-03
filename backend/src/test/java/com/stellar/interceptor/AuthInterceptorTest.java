package com.stellar.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.annotation.PublicAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthInterceptor} 单测：隔离 {@link StpUtil} 静态调用，
 * 验证「非 Controller 方法放行」「@PublicAccess（方法/类级）放行且不校验登录」「无注解则要求登录」。
 * 公共访问分支会打 debug 日志（读取 getBeanType().getSimpleName() / getMethod().getName()），
 * 故相关测试为 {@code HandlerMethod} 的 getMethod 提供真实方法对象，避免 NPE。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HandlerMethod handlerMethod;

    private static final Method PRE_HANDLE_METHOD;

    static {
        try {
            PRE_HANDLE_METHOD = AuthInterceptor.class.getMethod(
                    "preHandle", HttpServletRequest.class, HttpServletResponse.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void preHandle_非HandlerMethod_直接放行() {
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_方法标注PublicAccess_放行且不校验登录() {
        when(handlerMethod.getMethodAnnotation(PublicAccess.class)).thenReturn(mock(PublicAccess.class));
        doReturn(Object.class).when(handlerMethod).getBeanType();
        when(handlerMethod.getMethod()).thenReturn(PRE_HANDLE_METHOD);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            stp.verify(StpUtil::checkLogin, never());
        }
    }

    @Test
    void preHandle_类标注PublicAccess_放行且不校验登录() {
        when(handlerMethod.getMethodAnnotation(PublicAccess.class)).thenReturn(null);
        doReturn(PublicController.class).when(handlerMethod).getBeanType();
        when(handlerMethod.getMethod()).thenReturn(PRE_HANDLE_METHOD);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            stp.verify(StpUtil::checkLogin, never());
        }
    }

    @Test
    void preHandle_无注解_要求登录_调用checkLogin() {
        when(handlerMethod.getMethodAnnotation(PublicAccess.class)).thenReturn(null);
        doReturn(Object.class).when(handlerMethod).getBeanType();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            stp.verify(StpUtil::checkLogin);
        }
    }

    @PublicAccess
    static class PublicController {
    }
}
