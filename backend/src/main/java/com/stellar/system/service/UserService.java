package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.common.SecurityConstants;
import com.stellar.system.dto.ChangePasswordRequest;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUser getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 修改当前登录用户密码：校验旧密码后更新为新密码（BCrypt 加密）。
     */
    public void changePassword(ChangePasswordRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // 改密成功即清除强制改密标记（S3：默认口令首次登录强制改密）
        update.setMustChangePassword(0);
        update.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
        // 同步清除当前会话的强制改密拦截标记（DB 已清，session 标记不清则本会话仍被拦截）
        StpUtil.getSession().delete(SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD);
    }

    /**
     * 查全部用户（password 字段 @JsonIgnore 不返回）。供长期记忆等管理功能选用户。
     */
    public List<SysUser> listAll() {
        return sysUserMapper.selectList(null);
    }

    /**
     * 按 id 查用户，不存在返回 null（跨模块只读入口，调用方自行决定缺失语义）。
     */
    public SysUser getById(Long userId) {
        return userId == null ? null : sysUserMapper.selectById(userId);
    }

    /**
     * 批量取 userId → username 映射（空入参直接返回空 map），供列表页联查操作人名称。
     */
    public Map<Long, String> getUsernameMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(userIds)) {
            nameMap.put(u.getId(), u.getUsername());
        }
        return nameMap;
    }
}
