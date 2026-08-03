package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.ai.service.AiMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiMemoryController} 单测：分页/编辑/删除/手动整理透传，create 的 userId 类型校验分支。
 */
@ExtendWith(MockitoExtension.class)
class AiMemoryControllerTest {

    @Mock
    AiMemoryService memoryService;

    AiMemoryController controller;

    @BeforeEach
    void setup() {
        controller = new AiMemoryController(memoryService);
    }

    @Test
    void pageAll_正常() {
        when(memoryService.pageAll(1, 10)).thenReturn(new Page<>());
        assertNotNull(controller.pageAll(1, 10).getData());
    }

    @Test
    void pageByUser_正常() {
        when(memoryService.pageByUser(7L, 1, 10)).thenReturn(new Page<>());
        assertNotNull(controller.pageByUser(7L, 1, 10).getData());
    }

    @Test
    void update_正常() {
        controller.update(1L, Map.of("content", "新内容"));
        verify(memoryService).update(1L, "新内容");
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(memoryService).delete(1L);
    }

    @Test
    void create_userId为数字_正常() {
        controller.create(Map.of("userId", 5, "content", "记忆内容"));
        verify(memoryService).create(5L, "记忆内容");
    }

    @Test
    void create_userId非数字_抛BusinessException() {
        assertThrows(BusinessException.class, () -> controller.create(Map.of("userId", "abc", "content", "x")));
        verify(memoryService, never()).create(anyLong(), anyString());
    }

    @Test
    void summarizeSession_正常() {
        when(memoryService.summarizeSession(3L)).thenReturn(2);
        assertEquals(2, controller.summarizeSession(3L).getData());
    }
}
