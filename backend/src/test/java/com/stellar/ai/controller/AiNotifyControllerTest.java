package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.infra.SseEmitterManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * {@link AiNotifyController} 单测：登录按 account 订阅、游客按 IP 订阅（代理头穿透）。
 * <p>SSE 连接初始消息由 {@link SseEmitter#send} 发送，真实 emitter 可正常发送无需 mock。
 */
@ExtendWith(MockitoExtension.class)
class AiNotifyControllerTest {

    @Mock
    SseEmitterManager sseEmitterManager;
    @Mock
    HttpServletRequest request;

    AiNotifyController controller;

    @BeforeEach
    void setup() {
        controller = new AiNotifyController(sseEmitterManager);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void subscribe_登录_按account注册() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(sseEmitterManager.register("account:u1")).thenReturn(emitter);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            assertSame(emitter, controller.subscribe());
        }
        verify(sseEmitterManager).register("account:u1");
    }

    @Test
    void subscribe_游客_按IP注册() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6");
        SseEmitter emitter = new SseEmitter();
        when(sseEmitterManager.register("ip:6.6.6.6")).thenReturn(emitter);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertSame(emitter, controller.subscribe());
        }
        verify(sseEmitterManager).register("ip:6.6.6.6");
    }

    @Test
    void subscribe_游客_无代理头_回退remoteAddr() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");
        SseEmitter emitter = new SseEmitter();
        when(sseEmitterManager.register("ip:3.3.3.3")).thenReturn(emitter);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertSame(emitter, controller.subscribe());
        }
        verify(sseEmitterManager).register("ip:3.3.3.3");
    }
}
