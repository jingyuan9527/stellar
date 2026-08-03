package com.stellar.system.controller;

import com.stellar.system.service.SysSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link SysSettingController} 单测：get/set 透传（默认值 null 由 controller 固定传）。
 */
@ExtendWith(MockitoExtension.class)
class SysSettingControllerTest {

    @Mock
    SysSettingService sysSettingService;

    SysSettingController controller;

    @BeforeEach
    void setup() {
        controller = new SysSettingController(sysSettingService);
    }

    @Test
    void get_正常() {
        when(sysSettingService.get("conch_ai_enabled", null)).thenReturn("true");
        assertEquals("true", controller.get("conch_ai_enabled").getData());
        verify(sysSettingService).get("conch_ai_enabled", null);
    }

    @Test
    void set_正常() {
        controller.set("conch_ai_enabled", "false");
        verify(sysSettingService).set("conch_ai_enabled", "false", null);
    }
}
