package com.stellar.system.controller;

import com.stellar.system.dto.MenuVisibilityItemDTO;
import com.stellar.system.entity.SysMenuVisibility;
import com.stellar.system.service.MenuVisibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link MenuVisibilityController} 单测：list/batchUpdate 透传。
 */
@ExtendWith(MockitoExtension.class)
class MenuVisibilityControllerTest {

    @Mock
    MenuVisibilityService menuVisibilityService;

    MenuVisibilityController controller;

    @BeforeEach
    void setup() {
        controller = new MenuVisibilityController(menuVisibilityService);
    }

    @Test
    void list_正常() {
        when(menuVisibilityService.listAll()).thenReturn(List.of(new SysMenuVisibility()));
        assertEquals(1, controller.list().getData().size());
    }

    @Test
    void batchUpdate_正常() {
        List<MenuVisibilityItemDTO> items = List.of(new MenuVisibilityItemDTO());
        controller.batchUpdate(items);
        verify(menuVisibilityService).batchUpsert(items);
    }
}
