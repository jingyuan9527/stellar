package com.stellar.system.controller;

import com.stellar.system.dto.ProfileProjectDTO;
import com.stellar.system.entity.SysProfileProject;
import com.stellar.system.service.ProfileProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link ProfileProjectController} 单测：list/CRUD 透传。
 */
@ExtendWith(MockitoExtension.class)
class ProfileProjectControllerTest {

    @Mock
    ProfileProjectService profileProjectService;

    ProfileProjectController controller;

    @BeforeEach
    void setup() {
        controller = new ProfileProjectController(profileProjectService);
    }

    @Test
    void list_正常() {
        when(profileProjectService.list()).thenReturn(List.of(new SysProfileProject()));
        assertEquals(1, controller.list().getData().size());
    }

    @Test
    void create_正常() {
        ProfileProjectDTO dto = new ProfileProjectDTO();
        controller.create(dto);
        verify(profileProjectService).create(dto);
    }

    @Test
    void update_正常() {
        ProfileProjectDTO dto = new ProfileProjectDTO();
        controller.update(dto);
        verify(profileProjectService).update(dto);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(profileProjectService).delete(1L);
    }
}
