package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.AiBuiltinTemplates;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiTemplateDTO;
import com.stellar.ai.dto.AiTemplateQueryDTO;
import com.stellar.ai.entity.SysAiTemplate;
import com.stellar.ai.mapper.SysAiTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTemplateService {

    private final SysAiTemplateMapper templateMapper;

    public Page<SysAiTemplate> page(AiTemplateQueryDTO query) {
        Page<SysAiTemplate> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysAiTemplate> wrapper = new LambdaQueryWrapper<SysAiTemplate>()
                .like(StringUtils.hasText(query.getName()), SysAiTemplate::getName, query.getName())
                .eq(StringUtils.hasText(query.getPlatform()), SysAiTemplate::getPlatform, query.getPlatform())
                .orderByDesc(SysAiTemplate::getUpdateTime);
        return templateMapper.selectPage(page, wrapper);
    }

    public void create(AiTemplateDTO dto, Long creatorId) {
        SysAiTemplate template = new SysAiTemplate();
        template.setName(dto.getName());
        template.setPlatform(dto.getPlatform());
        template.setPrompt(dto.getPrompt());
        template.setBuiltIn(0);
        template.setCreatorId(creatorId);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
    }

    public void update(Long id, AiTemplateDTO dto) {
        SysAiTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setName(dto.getName());
        template.setPlatform(dto.getPlatform());
        template.setPrompt(dto.getPrompt());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    public void delete(Long id) {
        SysAiTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getBuiltIn() != null && template.getBuiltIn() == 1) {
            throw new BusinessException("内置模板不可删除");
        }
        templateMapper.deleteById(id);
    }

    /**
     * 恢复内置模板为默认配置。
     * <p>
     * 内置模板的 platform 即种子 key（bilibili/douyin/xiaohongshu），
     * 据此查找原始 name + prompt 并覆盖。
     */
    public void resetBuiltin(Long id) {
        SysAiTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getBuiltIn() == null || template.getBuiltIn() != 1) {
            throw new BusinessException("仅内置模板可恢复默认");
        }
        AiBuiltinTemplates.Seed seed = AiBuiltinTemplates.findByKey(template.getPlatform());
        if (seed == null) {
            throw new BusinessException("未找到内置模板的默认配置");
        }
        template.setName(seed.name());
        template.setPlatform(seed.platform());
        template.setPrompt(seed.prompt());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
    }
}
