package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.LoginRequest;
import com.stellar.dto.LoginResult;
import com.stellar.entity.SysUser;
import com.stellar.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginResult login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        LoginResult result = new LoginResult();
        result.setToken(StpUtil.getTokenValue());
        result.setUserInfo(user);
        return result;
    }

    public void logout() {
        StpUtil.logout();
    }
}
