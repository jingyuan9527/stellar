package com.stellar.ai.controller;

import com.stellar.ai.dto.AiModelDTO;
import com.stellar.ai.service.AiModelService;
import com.stellar.ai.vo.AiModelVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * {@link AiModelController} 单测：list 按 providerId 分支、按类型查启用模型、CRUD/启停/设默认透传。
 */
@ExtendWith(MockitoExtension.class)
class AiModelControllerTest {

    @Mock
    AiModelService aiModelService;

    AiModelController controller;

    @BeforeEach
    void setup() {
        controller = new AiModelController(aiModelService);
    }

    @Test
    void list_带providerId_按供应商查() {
        when(aiModelService.listByProvider(2L)).thenReturn(List.of(new AiModelVO()));
        assertEquals(1, controller.list(2L).getData().size());
        verify(aiModelService, never()).listAll();
    }

    @Test
    void list_不带providerId_查全部() {
        when(aiModelService.listAll()).thenReturn(List.of(new AiModelVO()));
        assertEquals(1, controller.list(null).getData().size());
        verify(aiModelService, never()).listByProvider(anyLong());
    }

    @Test
    void listByType_正常() {
        when(aiModelService.listEnabledByType("IMAGE")).thenReturn(List.of(new AiModelVO()));
        assertNotNull(controller.listByType("IMAGE").getData());
    }

    @Test
    void create_正常() {
        AiModelDTO dto = new AiModelDTO();
        controller.create(dto);
        verify(aiModelService).create(dto);
    }

    @Test
    void update_正常() {
        AiModelDTO dto = new AiModelDTO();
        controller.update(dto);
        verify(aiModelService).update(dto);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(aiModelService).delete(1L);
    }

    @Test
    void toggleEnabled_正常() {
        controller.toggleEnabled(1L, 0);
        verify(aiModelService).toggleEnabled(1L, 0);
    }

    @Test
    void setDefault_正常() {
        controller.setDefault(1L);
        verify(aiModelService).setDefault(1L);
    }
}
