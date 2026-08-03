package com.stellar.ai.controller;

import com.stellar.ai.dto.AiPersonaDTO;
import com.stellar.ai.entity.AiPersona;
import com.stellar.ai.service.AiPersonaService;
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
 * {@link AiPersonaController} 单测：公开启用列表、全量列表、CRUD/启停/恢复内置透传。
 */
@ExtendWith(MockitoExtension.class)
class AiPersonaControllerTest {

    @Mock
    AiPersonaService aiPersonaService;

    AiPersonaController controller;

    @BeforeEach
    void setup() {
        controller = new AiPersonaController(aiPersonaService);
    }

    @Test
    void listEnabled_正常() {
        when(aiPersonaService.listEnabled()).thenReturn(List.of(new AiPersona()));
        assertEquals(1, controller.listEnabled().getData().size());
    }

    @Test
    void listAll_正常() {
        when(aiPersonaService.listAll()).thenReturn(List.of(new AiPersona()));
        assertNotNull(controller.listAll().getData());
    }

    @Test
    void create_正常() {
        AiPersonaDTO dto = new AiPersonaDTO();
        controller.create(dto);
        verify(aiPersonaService).create(dto);
    }

    @Test
    void update_正常() {
        AiPersonaDTO dto = new AiPersonaDTO();
        controller.update(dto);
        verify(aiPersonaService).update(dto);
    }

    @Test
    void toggleEnabled_正常() {
        controller.toggleEnabled(1L, 0);
        verify(aiPersonaService).toggleEnabled(1L, 0);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(aiPersonaService).delete(1L);
    }

    @Test
    void resetBuiltin_正常() {
        controller.resetBuiltin(1L);
        verify(aiPersonaService).resetBuiltin(1L);
    }
}
