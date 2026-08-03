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
 * {@link AiChatRecordController} 单测：文案历史分页/删除/清空，固定 taskType=text，按登录/游客解析主体。
 */
@ExtendWith(MockitoExtension.class)
class AiChatRecordControllerTest {

    @Mock
    AiTaskService aiTaskService;
    @Mock
    HttpServletRequest request;

    AiChatRecordController controller;

    @BeforeEach
    void setup() {
        controller = new AiChatRecordController(aiTaskService);
    }

    @Test
    void page_登录_固定text类型() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            when(aiTaskService.page("text", "account", "u1", 1, 10)).thenReturn(new Page<>());
            assertNotNull(controller.page(1, 10, request).getData());
        }
        verify(aiTaskService).page("text", "account", "u1", 1, 10);
    }

    @Test
    void page_游客() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("4.4.4.4");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            when(aiTaskService.page("text", "ip", "4.4.4.4", 1, 10)).thenReturn(new Page<>());
            assertNotNull(controller.page(1, 10, request).getData());
        }
    }

    @Test
    void delete_游客() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("4.4.4.4");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.delete(1L, request);
        }
        verify(aiTaskService).delete(1L, "ip", "4.4.4.4");
    }

    @Test
    void clear_登录() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            controller.clear(request);
        }
        verify(aiTaskService).clear("text", "account", "u1");
    }
}
