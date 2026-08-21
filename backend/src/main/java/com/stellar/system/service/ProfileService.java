package com.stellar.system.service;

import com.stellar.system.dto.ProfileDTO;
import com.stellar.system.entity.SysProfile;
import com.stellar.system.mapper.SysProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 个人介绍服务：单条配置（id=1），upsert 语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SysProfileMapper profileMapper;

    /**
     * 获取个人介绍（不存在则返回空对象，id=1 占位）。公开页/后台共用。
     * <p>不走 Spring Cache：该方法返回单个 POJO，而缓存 value 序列化器关闭了 default
     * typing（见 RedisConfig S2 安全决策），读回会退化成 {@code LinkedHashMap}，
     * 缓存切面强转方法返回类型 {@link SysProfile} 抛 ClassCastException（如
     * {@code GET /public/profile} 曾 500）。profile 是单行配置、selectById 极廉价，
     * 直接查库，无缓存收益。
     */
    public SysProfile get() {
        SysProfile p = profileMapper.selectById(1L);
        if (p == null) {
            p = new SysProfile();
            p.setId(1L);
        }
        return p;
    }

    /** 更新或初始化（id=1）。 */
    public void update(ProfileDTO dto) {
        SysProfile p = profileMapper.selectById(1L);
        if (p == null) {
            p = new SysProfile();
            p.setId(1L);
            applyDto(p, dto);
            p.setUpdateTime(LocalDateTime.now());
            profileMapper.insert(p);
        } else {
            applyDto(p, dto);
            p.setUpdateTime(LocalDateTime.now());
            profileMapper.updateById(p);
        }
        log.info("[个人介绍] 已更新 nickname={}, title={}", dto.getNickname(), dto.getTitle());
    }

    private void applyDto(SysProfile p, ProfileDTO dto) {
        p.setNickname(dto.getNickname());
        p.setAvatar(dto.getAvatar());
        p.setBio(dto.getBio());
        p.setSkills(dto.getSkills());
        p.setLinks(dto.getLinks());
        p.setTitle(dto.getTitle());
        p.setAbout(dto.getAbout());
        p.setLocation(dto.getLocation());
    }
}
