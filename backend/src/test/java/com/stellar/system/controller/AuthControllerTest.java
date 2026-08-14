package com.stellar.system.controller;

import com.stellar.system.dto.LoginRequest;
import com.stellar.system.dto.LoginResult;
import com.stellar.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link AuthController} 单测：login 透传 request 返回 token，logout 调用服务。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;
    @Mock
    HttpServletRequest httpRequest;

    AuthController controller;

    @BeforeEach
    void setup() {
        controller = new AuthController(authService);
    }

    @Test
    void login_正常_返回token() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("123456");
        LoginResult result = new LoginResult();
        result.setToken("tk-1");
        when(authService.login(req, httpRequest)).thenReturn(result);

        LoginResult r = controller.login(req, httpRequest).getData();

        assertEquals("tk-1", r.getToken());
        verify(authService).login(req, httpRequest);
    }

    @Test
    void logout_正常() {
        controller.logout();
        verify(authService).logout();
    }
}