package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.AiBuiltinPersonas;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiPersonaDTO;
import com.stellar.ai.entity.AiPersona;
import com.stellar.ai.mapper.AiPersonaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 人设服务：公开查启用列表（聊天页下拉），登录管理 CRUD + 内置恢复默认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPersonaService {

    private final AiPersonaMapper personaMapper;

    /**
     * 查启用的人设列表（聊天页下拉用，公开接口高频读，走 Spring Cache）。
     */
    @Cacheable(cacheNames = "ai-persona", key = "'enabled'")
    public List<AiPersona> listEnabled() {
        List<AiPersona> list = personaMapper.selectList(new LambdaQueryWrapper<AiPersona>()
                .eq(AiPersona::getEnabled, 1)
                .orderByAsc(AiPersona::getSortOrder)
                .orderByAsc(AiPersona::getId));
        return list.stream().collect(Collectors.toList());
    }

    /**
     * 管理后台查全部（含禁用）。
     */
    @Cacheable(cacheNames = "ai-persona", key = "'all'")
    public List<AiPersona> listAll() {
        return personaMapper.selectList(new LambdaQueryWrapper<AiPersona>()
                .orderByAsc(AiPersona::getSortOrder)
                .orderByAsc(AiPersona::getId))
                .stream().collect(Collectors.toList());
    }

    @CacheEvict(cacheNames = "ai-persona", allEntries = true)
    public void create(AiPersonaDTO dto) {
        AiPersona p = new AiPersona();
        p.setName(dto.getName().trim());
        p.setSystemPrompt(dto.getSystemPrompt());
        p.setDescription(dto.getDescription());
        p.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        p.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        p.setBuiltIn(0);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        personaMapper.insert(p);
        log.info("[AI人设] 新增 id={} name={}", p.getId(), p.getName());
    }

    @CacheEvict(cacheNames = "ai-persona", allEntries = true)
    public void update(AiPersonaDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("人设 id 不能为空");
        }
        AiPersona exist = personaMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("人设不存在");
        }
        exist.setName(dto.getName().trim());
        exist.setSystemPrompt(dto.getSystemPrompt());
        exist.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) {
            exist.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            exist.setSortOrder(dto.getSortOrder());
        }
        exist.setUpdateTime(LocalDateTime.now());
        personaMapper.updateById(exist);
        log.info("[AI人设] 更新 id={} name={}", exist.getId(), exist.getName());
    }

    @CacheEvict(cacheNames = "ai-persona", allEntries = true)
    public void toggleEnabled(Long id, Integer enabled) {
        AiPersona exist = personaMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("人设不存在");
        }
        exist.setEnabled(enabled);
        exist.setUpdateTime(LocalDateTime.now());
        personaMapper.updateById(exist);
        log.info("[AI人设] 切换启用 id={} enabled={}", id, enabled);
    }

    @CacheEvict(cacheNames = "ai-persona", allEntries = true)
    public void delete(Long id) {
        AiPersona exist = personaMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("人设不存在");
        }
        if (exist.getBuiltIn() != null && exist.getBuiltIn() == 1) {
            throw new BusinessException("内置人设不可删除");
        }
        personaMapper.deleteById(id);
        log.info("[AI人设] 删除 id={}", id);
    }

    /**
     * 恢复内置人设为默认配置。
     */
    @CacheEvict(cacheNames = "ai-persona", allEntries = true)
    public void resetBuiltin(Long id) {
        AiPersona exist = personaMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("人设不存在");
        }
        if (exist.getBuiltIn() == null || exist.getBuiltIn() != 1) {
            throw new BusinessException("仅内置人设可恢复默认");
        }
        AiBuiltinPersonas.Seed seed = AiBuiltinPersonas.findByName(exist.getName());
        if (seed == null) {
            throw new BusinessException("未找到内置人设的默认配置");
        }
        exist.setSystemPrompt(seed.systemPrompt());
        exist.setDescription(seed.description());
        exist.setSortOrder(seed.sortOrder());
        exist.setUpdateTime(LocalDateTime.now());
        personaMapper.updateById(exist);
        log.info("[AI人设] 恢复默认 id={} name={}", id, exist.getName());
    }
}
