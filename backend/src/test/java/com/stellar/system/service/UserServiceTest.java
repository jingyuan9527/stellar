package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.system.dto.ChangePasswordRequest;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link UserService} 单测：getCurrentUser 不存在抛、changePassword 旧密码校验与更新、
 * listAll 全量。StpUtil 用 MockedStatic 模拟登录态。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    SysUserMapper sysUserMapper;
    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void getCurrentUser_不存在_抛() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(sysUserMapper.selectById(1L)).thenReturn(null);
            assertThrows(BusinessException.class, service::getCurrentUser);
        }
    }

    @Test
    void getCurrentUser_正常_返回用户() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(sysUserMapper.selectById(1L)).thenReturn(u);
            assertEquals("admin", service.getCurrentUser().getUsername());
        }
    }

    @Test
    void changePassword_用户不存在_抛() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old");
        req.setNewPassword("newpass");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(sysUserMapper.selectById(1L)).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.changePassword(req));
        }
    }

    @Test
    void changePassword_旧密码错_抛() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPassword("old-hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong");
        req.setNewPassword("newpass");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(sysUserMapper.selectById(1L)).thenReturn(u);
            when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.changePassword(req));
        }
    }

    @Test
    void changePassword_成功_更新密码() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPassword("old-hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old");
        req.setNewPassword("newpass");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(sysUserMapper.selectById(1L)).thenReturn(u);
            when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("new-hash");

            service.changePassword(req);

            ArgumentCaptor<SysUser> cap = ArgumentCaptor.forClass(SysUser.class);
            verify(sysUserMapper).updateById(cap.capture());
            assertEquals(1L, cap.getValue().getId());
            assertEquals("new-hash", cap.getValue().getPassword());
            assertEquals(Integer.valueOf(0), cap.getValue().getMustChangePassword());
        }
    }

    @Test
    void listAll_返回全部用户() {
        UserService service = new UserService(sysUserMapper, passwordEncoder);
        when(sysUserMapper.selectList(null)).thenReturn(List.of(new SysUser()));
        assertEquals(1, service.listAll().size());
    }
}
