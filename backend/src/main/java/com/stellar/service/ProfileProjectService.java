package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.dto.ProfileProjectDTO;
import com.stellar.entity.SysProfileProject;
import com.stellar.mapper.SysProfileProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 个人项目展示服务：about 页公开展示，管理后台 CRUD。
 * <p>读多写少，列表走 Spring Cache（cacheName=profile-project，与 profile 互不冲突）；
 * 写操作清空全部项目缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileProjectService {

    private final SysProfileProjectMapper mapper;

    /**
     * 全量列表（按 id 升序，先加的排前面）。公开页/后台共用，走 Spring Cache。
     * <p>注意：@Cacheable 方法返回 List 必须用 Collectors.toList()（ArrayList），
     * 不可用 Stream.toList()（ImmutableCollections 反序列化失败）。
     */
    @Cacheable(cacheNames = "profile-project", key = "'list'")
    public List<SysProfileProject> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysProfileProject>()
                .orderByAsc(SysProfileProject::getId))
                .stream()
                .collect(Collectors.toList());
    }

    @CacheEvict(cacheNames = "profile-project", allEntries = true)
    public void create(ProfileProjectDTO dto) {
        SysProfileProject entity = new SysProfileProject();
        applyDto(entity, dto);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mapper.insert(entity);
        log.info("[个人项目] 新增 id={}, name={}", entity.getId(), dto.getName());
    }

    @CacheEvict(cacheNames = "profile-project", allEntries = true)
    public void update(ProfileProjectDTO dto) {
        SysProfileProject entity = mapper.selectById(dto.getId());
        if (entity == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        applyDto(entity, dto);
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);
        log.info("[个人项目] 更新 id={}, name={}", entity.getId(), dto.getName());
    }

    @CacheEvict(cacheNames = "profile-project", allEntries = true)
    public void delete(Long id) {
        mapper.deleteById(id);
        log.info("[个人项目] 删除 id={}", id);
    }

    private void applyDto(SysProfileProject entity, ProfileProjectDTO dto) {
        entity.setName(dto.getName());
        entity.setSiteUrl(dto.getSiteUrl());
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setDescription(dto.getDescription());
    }
}
