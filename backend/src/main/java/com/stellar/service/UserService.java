package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.entity.SysUser;
import com.stellar.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;

    public SysUser getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
