package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.stellar.common.BusinessException;
import com.stellar.system.dto.LoginRequest;
import com.stellar.system.dto.LoginResult;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthService} 单测：登录的三种失败分支（用户不存在/密码错/禁用）、成功登录拿 token、
 * 登出。登录态用 MockedStatic 模拟 StpUtil。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    SysUserMapper sysUserMapper;
    @Mock
    PasswordEncoder passwordEncoder;

    private SysUser user(Long id, String username, String password, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(password);
        u.setStatus(status);
        return u;
    }

    @Test
    void login_用户不存在_抛() {
        AuthService service = new AuthService(sysUserMapper, passwordEncoder);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("x");

        assertThrows(BusinessException.class, () -> service.login(req));
    }

    @Test
    void login_密码不匹配_抛() {
        AuthService service = new AuthService(sysUserMapper, passwordEncoder);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user(1L, "admin", "hash", 1));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        assertThrows(BusinessException.class, () -> service.login(req));
    }

    @Test
    void login_账号禁用_抛() {
        AuthService service = new AuthService(sysUserMapper, passwordEncoder);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user(1L, "admin", "hash", 0));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("p");

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(req));
        assertEquals("账号已被禁用", e.getMessage());
    }

    @Test
    void login_成功_返回token与用户() {
        AuthService service = new AuthService(sysUserMapper, passwordEncoder);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user(1L, "admin", "hash", 1));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("p");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getTokenValue()).thenReturn("tok-123");
            LoginResult result = service.login(req);
            stp.verify(() -> StpUtil.login(1L));
            assertEquals("tok-123", result.getToken());
            assertEquals("admin", result.getUserInfo().getUsername());
        }
    }

    @Test
    void logout_调用StpUtil登出() {
        AuthService service = new AuthService(sysUserMapper, passwordEncoder);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.logout();
            stp.verify(StpUtil::logout);
        }
    }
}
