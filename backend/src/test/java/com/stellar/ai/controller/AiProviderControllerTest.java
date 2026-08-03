package com.stellar.ai.controller;

import com.stellar.ai.dto.AiProviderDTO;
import com.stellar.ai.service.AiProviderService;
import com.stellar.ai.vo.AiProviderVO;
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
 * {@link AiProviderController} 单测：CRUD/启停/拉模型/测试连通透传。
 */
@ExtendWith(MockitoExtension.class)
class AiProviderControllerTest {

    @Mock
    AiProviderService aiProviderService;

    AiProviderController controller;

    @BeforeEach
    void setup() {
        controller = new AiProviderController(aiProviderService);
    }

    @Test
    void list_正常() {
        when(aiProviderService.list()).thenReturn(List.of(new AiProviderVO()));
        assertNotNull(controller.list().getData());
    }

    @Test
    void create_正常() {
        AiProviderDTO dto = new AiProviderDTO();
        controller.create(dto);
        verify(aiProviderService).create(dto);
    }

    @Test
    void update_正常() {
        AiProviderDTO dto = new AiProviderDTO();
        controller.update(dto);
        verify(aiProviderService).update(dto);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(aiProviderService).delete(1L);
    }

    @Test
    void toggleEnabled_正常() {
        controller.toggleEnabled(1L, 1);
        verify(aiProviderService).toggleEnabled(1L, 1);
    }

    @Test
    void fetchModels_正常() {
        when(aiProviderService.fetchModels(1L)).thenReturn(List.of("gpt-4o"));
        assertEquals(1, controller.fetchModels(1L).getData().size());
    }

    @Test
    void test_带model() {
        controller.test(1L, "m");
        verify(aiProviderService).testConnection(1L, "m");
    }

    @Test
    void test_不带model() {
        controller.test(1L, null);
        verify(aiProviderService).testConnection(1L, null);
    }
}
