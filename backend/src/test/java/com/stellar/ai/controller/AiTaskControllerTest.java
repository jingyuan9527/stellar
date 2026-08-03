package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.service.AiTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * {@link AiTaskController} 单测：page/delete/clear 按登录/游客解析主体（SubjectUtils 内部走 StpUtil）。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskControllerTest {

    @Mock
    AiTaskService aiTaskService;
    @Mock
    HttpServletRequest request;

    AiTaskController controller;

    @BeforeEach
    void setup() {
        controller = new AiTaskController(aiTaskService);
    }

    @Test
    void page_登录_按账号查() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            when(aiTaskService.page("text", "account", "u1", 1, 10)).thenReturn(new Page<>());
            assertNotNull(controller.page("text", 1, 10, request).getData());
        }
        verify(aiTaskService).page("text", "account", "u1", 1, 10);
    }

    @Test
    void page_游客_按IP查() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("7.7.7.7");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            when(aiTaskService.page("image", "ip", "7.7.7.7", 1, 20)).thenReturn(new Page<>());
            assertNotNull(controller.page("image", 1, 20, request).getData());
        }
    }

    @Test
    void delete_登录() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            controller.delete(1L, request);
        }
        verify(aiTaskService).delete(1L, "account", "u1");
    }

    @Test
    void clear_游客() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("7.7.7.7");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.clear("video", request);
        }
        verify(aiTaskService).clear("video", "ip", "7.7.7.7");
    }
}
