package com.stellar.service;

import com.stellar.dto.ProfileDTO;
import com.stellar.entity.SysProfile;
import com.stellar.mapper.SysProfileMapper;
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

    /** 获取个人介绍（不存在则返回空对象，id=1 占位） */
    public SysProfile get() {
        SysProfile p = profileMapper.selectById(1L);
        if (p == null) {
            p = new SysProfile();
            p.setId(1L);
        }
        return p;
    }

    /** 更新或初始化（id=1） */
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
        log.info("[个人介绍] 已更新 nickname={}", dto.getNickname());
    }

    private void applyDto(SysProfile p, ProfileDTO dto) {
        p.setNickname(dto.getNickname());
        p.setAvatar(dto.getAvatar());
        p.setBio(dto.getBio());
        p.setSkills(dto.getSkills());
        p.setLinks(dto.getLinks());
    }
}
