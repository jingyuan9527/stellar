package com.stellar.system.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.stellar.common.BusinessException;
import com.stellar.common.SecurityConstants;
import com.stellar.system.dto.LoginRequest;
import com.stellar.system.dto.LoginResult;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthService} 单测：登录的三种失败分支（用户不存在/密码错/禁用）、成功登录拿 token、
 * 爆破防护（IP 尝试窗口超限 / 账号失败锁定 / 失败计数 / 成功清空计数）、登出。
 * 登录态用 MockedStatic 模拟 StpUtil；Redis 用 mock StringRedisTemplate。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    SysUserMapper sysUserMapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    StringRedisTemplate stringRedisTemplate;
    @Mock
    ValueOperations<String, String> valueOps;
    @Mock
    HttpServletRequest httpRequest;

    AuthService service;

    @BeforeEach
    void setup() {
        // lenient：logout 等个别用例不使用这两个 stub，避免 UnnecessaryStubbing
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");
        // WebUtils：X-Forwarded-For / X-Real-IP 为 null（mock 默认），走 getRemoteAddr
        service = new AuthService(sysUserMapper, passwordEncoder, stringRedisTemplate);
    }

    private SysUser user(Long id, String username, String password, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(password);
        u.setStatus(status);
        return u;
    }

    private LoginRequest req(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    @Test
    void login_用户不存在_抛并记录失败() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.login(req("nobody", "x"), httpRequest));
        verify(valueOps).increment(AuthService.LOGIN_FAIL_PREFIX + "nobody");
    }

    @Test
    void login_密码不匹配_抛并记录失败() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user(1L, "admin", "hash", 1));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.login(req("admin", "wrong"), httpRequest));
        verify(valueOps).increment(AuthService.LOGIN_FAIL_PREFIX + "admin");
    }

    @Test
    void login_账号禁用_抛且不计数() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user(1L, "admin", "hash", 0));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(req("admin", "p"), httpRequest));
        assertEquals("账号已被禁用", e.getMessage());
        verify(valueOps, never()).increment(AuthService.LOGIN_FAIL_PREFIX + "admin");
    }

    @Test
    void login_IP尝试超阈值_抛频繁() {
        when(valueOps.increment(AuthService.LOGIN_ATTEMPT_PREFIX + "1.2.3.4")).thenReturn(11L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(req("admin", "p"), httpRequest));
        assertTrue(e.getMessage().contains("过于频繁"));
        verify(sysUserMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void login_失败计数达阈值_锁定抛() {
        when(valueOps.get(AuthService.LOGIN_FAIL_PREFIX + "admin")).thenReturn("5");

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(req("admin", "p"), httpRequest));
        assertTrue(e.getMessage().contains("锁定"));
        verify(sysUserMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void login_成功_返回token并清空失败计数() {
        SysUser u = user(1L, "admin", "hash", 1);
        u.setMustChangePassword(1);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(u);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        SaSession session = mock(SaSession.class);
        LoginRequest request = req("admin", "p");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getTokenValue()).thenReturn("tok-123");
            stp.when(StpUtil::getSession).thenReturn(session);
            LoginResult result = service.login(request, httpRequest);
            stp.verify(() -> StpUtil.login(1L));
            assertEquals("tok-123", result.getToken());
            assertEquals("admin", result.getUserInfo().getUsername());
            assertEquals(Integer.valueOf(1), result.getUserInfo().getMustChangePassword());
            // S3 真·强制：首登标记未清，会话写入拦截标记
            verify(session).set(SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD, Boolean.TRUE);
        }
        verify(stringRedisTemplate).delete(AuthService.LOGIN_FAIL_PREFIX + "admin");
        verify(stringRedisTemplate).delete(AuthService.LOGIN_ATTEMPT_PREFIX + "1.2.3.4");
    }

    @Test
    void login_成功_无强制改密标记_不写会话标记() {
        SysUser u = user(1L, "admin", "hash", 1);
        u.setMustChangePassword(0);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(u);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        SaSession session = mock(SaSession.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            service.login(req("admin", "p"), httpRequest);
            verify(session, never()).set(SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD, Boolean.TRUE);
        }
    }

    @Test
    void logout_调用StpUtil登出() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.logout();
            stp.verify(StpUtil::logout);
        }
    }
}