package com.stellar.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.entity.SysUser;
import com.stellar.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /** 设了则启动时重置 admin 密码为该值（环境变量 ADMIN_PASSWORD 注入） */
    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // 设了 ADMIN_PASSWORD 则重置 admin 密码（部署时改密码用）
        if (StringUtils.hasText(adminPassword)) {
            SysUser existing = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
            if (existing != null) {
                existing.setPassword(passwordEncoder.encode(adminPassword));
                existing.setUpdateTime(LocalDateTime.now());
                sysUserMapper.updateById(existing);
                log.info("已根据 ADMIN_PASSWORD 重置管理员密码");
                return;
            }
        }

        // admin 不存在则创建默认账号
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname("管理员");
        user.setAvatar("");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        log.info("已初始化管理员账号: admin / 123456");
    }
}
