package com.stellar.system.controller;

import com.stellar.system.dto.ChangePasswordRequest;
import com.stellar.system.entity.SysUser;
import com.stellar.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link UserController} 单测：info/list/changePassword 透传。
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserService userService;

    UserController controller;

    @BeforeEach
    void setup() {
        controller = new UserController(userService);
    }

    @Test
    void info_正常() {
        SysUser u = new SysUser();
        u.setUsername("admin");
        when(userService.getCurrentUser()).thenReturn(u);
        assertEquals("admin", controller.info().getData().getUsername());
    }

    @Test
    void list_正常() {
        when(userService.listAll()).thenReturn(List.of(new SysUser()));
        assertEquals(1, controller.list().getData().size());
    }

    @Test
    void changePassword_正常() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        controller.changePassword(req);
        verify(userService).changePassword(req);
    }
}
