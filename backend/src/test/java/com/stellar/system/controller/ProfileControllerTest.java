package com.stellar.system.controller;

import com.stellar.system.dto.ProfileDTO;
import com.stellar.system.entity.SysProfile;
import com.stellar.system.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link ProfileController} 单测：get/update 透传。
 */
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    ProfileService profileService;

    ProfileController controller;

    @BeforeEach
    void setup() {
        controller = new ProfileController(profileService);
    }

    @Test
    void get_正常() {
        SysProfile p = new SysProfile();
        p.setNickname("stellar");
        when(profileService.get()).thenReturn(p);
        assertEquals("stellar", controller.get().getData().getNickname());
    }

    @Test
    void update_正常() {
        ProfileDTO dto = new ProfileDTO();
        controller.update(dto);
        verify(profileService).update(dto);
    }
}
