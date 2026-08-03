package com.stellar.system.controller;

import com.stellar.system.entity.SysProfile;
import com.stellar.system.entity.SysProfileProject;
import com.stellar.system.service.MenuVisibilityService;
import com.stellar.system.service.ProfileProjectService;
import com.stellar.system.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link PublicController} 单测：三个公开只读接口的透传。
 */
@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock
    MenuVisibilityService menuVisibilityService;
    @Mock
    ProfileService profileService;
    @Mock
    ProfileProjectService profileProjectService;

    PublicController controller;

    @BeforeEach
    void setup() {
        controller = new PublicController(menuVisibilityService, profileService, profileProjectService);
    }

    @Test
    void menuConfig_正常() {
        when(menuVisibilityService.listPublicRouteKeys()).thenReturn(List.of("home", "game"));
        assertEquals(2, controller.menuConfig().getData().size());
    }

    @Test
    void profile_正常() {
        SysProfile p = new SysProfile();
        p.setId(1L);
        when(profileService.get()).thenReturn(p);
        assertEquals(1L, controller.profile().getData().getId());
    }

    @Test
    void profileProjects_正常() {
        when(profileProjectService.list()).thenReturn(List.of(new SysProfileProject()));
        assertEquals(1, controller.profileProjects().getData().size());
    }
}
