package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.AiVideoCreateDTO;
import com.stellar.ai.dto.AiVideoHistoryQueryDTO;
import com.stellar.ai.service.AiVideoService;
import com.stellar.ai.vo.AiVideoHistoryVO;
import com.stellar.ai.vo.AiVideoStatusVO;
import com.stellar.ai.vo.AiVideoTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AiVideoController} 单测：create/status 透传；page/delete 需登录，按账号解析主体（StpUtil）。
 */
@ExtendWith(MockitoExtension.class)
class AiVideoControllerTest {

    @Mock
    AiVideoService aiVideoService;

    AiVideoController controller;

    @BeforeEach
    void setup() {
        controller = new AiVideoController(aiVideoService);
    }

    @Test
    void create_正常() {
        AiVideoCreateDTO dto = new AiVideoCreateDTO();
        dto.setModelId(2L);
        dto.setPrompt("p");
        AiVideoTaskVO vo = new AiVideoTaskVO();
        vo.setVideoId("v1");
        when(aiVideoService.createTask(dto)).thenReturn(vo);
        assertEquals("v1", controller.create(dto).getData().getVideoId());
    }

    @Test
    void status_正常_先校验归属() {
        AiVideoStatusVO vo = new AiVideoStatusVO();
        vo.setStatus("completed");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            when(aiVideoService.getTask(2L, "v1")).thenReturn(vo);
            assertEquals("completed", controller.status(2L, "v1").getData().getStatus());
        }
        verify(aiVideoService).assertVideoOwner("v1", "account", "u1");
        verify(aiVideoService).getTask(2L, "v1");
    }

    @Test
    void status_归属校验抛出_不透传到查询() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            doThrow(new RuntimeException("无权访问该视频任务"))
                    .when(aiVideoService).assertVideoOwner("v1", "account", "u1");
            assertThrows(RuntimeException.class, () -> controller.status(2L, "v1"));
        }
        verify(aiVideoService, never()).getTask(any(), any());
    }

    @Test
    void page_需登录_按账号查() {
        AiVideoHistoryQueryDTO query = new AiVideoHistoryQueryDTO();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            when(aiVideoService.pageHistory(query, "account", "u1")).thenReturn(new Page<>());
            assertNotNull(controller.page(query).getData());
        }
        verify(aiVideoService).pageHistory(any(), eq("account"), eq("u1"));
    }

    @Test
    void delete_需登录_按账号删() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            controller.delete(3L);
        }
        verify(aiVideoService).deleteTask(3L, "account", "u1");
    }
}
