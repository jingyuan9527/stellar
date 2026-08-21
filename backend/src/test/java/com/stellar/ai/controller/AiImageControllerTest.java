package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.AiImageGenerateDTO;
import com.stellar.ai.dto.AiImageHistoryQueryDTO;
import com.stellar.ai.service.AiImageService;
import com.stellar.ai.vo.AiImageTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiImageController} 单测：登录/游客两态下 page/delete 的主体解析（StpUtil + IP 代理头穿透），
 * create/task 透传。getClientIp 逻辑与 RateLimitInterceptor 一致，用真实解析验证代理头链。
 */
@ExtendWith(MockitoExtension.class)
class AiImageControllerTest {

    @Mock
    AiImageService aiImageService;
    @Mock
    HttpServletRequest request;

    AiImageController controller;

    @BeforeEach
    void setup() {
        controller = new AiImageController(aiImageService);
    }

    @Test
    void create_正常() {
        AiImageGenerateDTO dto = new AiImageGenerateDTO();
        dto.setModelId(2L);
        dto.setPrompt("p");
        dto.setSize("1K");
        dto.setRatio("1:1");
        when(aiImageService.createTask(2L, "p", "1K", "1:1")).thenReturn(9L);

        assertEquals(9L, controller.create(dto).getData());
        verify(aiImageService).createTask(2L, "p", "1K", "1:1");
    }

    @Test
    void task_正常() {
        AiImageTaskVO vo = new AiImageTaskVO();
        vo.setStatus("completed");
        when(aiImageService.getTask(1L)).thenReturn(vo);
        assertEquals("completed", controller.task(1L).getData().getStatus());
    }

    @Test
    void page_登录_按账号查() {
        AiImageHistoryQueryDTO query = new AiImageHistoryQueryDTO();
        when(aiImageService.pageHistory(query, "account", "u1")).thenReturn(new Page<>());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            assertNotNull(controller.page(query, request).getData());
        }
        verify(aiImageService).pageHistory(query, "account", "u1");
    }

    @Test
    void page_游客_按IP查_代理头优先() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        AiImageHistoryQueryDTO query = new AiImageHistoryQueryDTO();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.page(query, request);
        }
        verify(aiImageService).pageHistory(query, "ip", "9.9.9.9");
    }

    @Test
    void page_游客_无代理头_回退remoteAddr() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        AiImageHistoryQueryDTO query = new AiImageHistoryQueryDTO();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.page(query, request);
        }
        verify(aiImageService).pageHistory(query, "ip", "127.0.0.1");
    }

    @Test
    void page_游客_仅XRealIP() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("  ");
        when(request.getHeader("X-Real-IP")).thenReturn("8.8.8.8");
        AiImageHistoryQueryDTO query = new AiImageHistoryQueryDTO();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.page(query, request);
        }
        verify(aiImageService).pageHistory(query, "ip", "8.8.8.8");
    }

    @Test
    void delete_登录_按账号删() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            controller.delete(3L, request);
        }
        verify(aiImageService).deleteTask(3L, "account", "u1");
    }

    @Test
    void delete_游客_按IP删() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("5.5.5.5");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.delete(3L, request);
        }
        verify(aiImageService).deleteTask(3L, "ip", "5.5.5.5");
    }
}
