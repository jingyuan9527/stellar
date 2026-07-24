package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.dto.AiCopyResultQueryDTO;
import com.stellar.dto.AiCopyResultSaveDTO;
import com.stellar.entity.SysAiCopyResult;
import com.stellar.mapper.SysAiCopyResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopyResultService {

    private final SysAiCopyResultMapper copyResultMapper;

    public Page<SysAiCopyResult> page(AiCopyResultQueryDTO query, Long creatorId) {
        Page<SysAiCopyResult> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysAiCopyResult> wrapper = new LambdaQueryWrapper<SysAiCopyResult>()
                .eq(SysAiCopyResult::getCreatorId, creatorId)
                .orderByDesc(SysAiCopyResult::getCreateTime);
        return copyResultMapper.selectPage(page, wrapper);
    }

    public void save(AiCopyResultSaveDTO dto, Long creatorId) {
        SysAiCopyResult result = new SysAiCopyResult();
        result.setTopic(dto.getTopic());
        result.setTemplateId(dto.getTemplateId());
        result.setResult(dto.getResult());
        result.setGeneratedAt(System.currentTimeMillis());
        result.setCreatorId(creatorId);
        result.setCreateTime(LocalDateTime.now());
        result.setUpdateTime(LocalDateTime.now());
        copyResultMapper.insert(result);
    }

    public void delete(Long id, Long creatorId) {
        SysAiCopyResult result = copyResultMapper.selectById(id);
        if (result == null) {
            return;
        }
        if (!creatorId.equals(result.getCreatorId())) {
            log.warn("用户 {} 试图删除非自己的文案记录 {}", creatorId, id);
            return;
        }
        copyResultMapper.deleteById(id);
    }

    public void clear(Long creatorId) {
        copyResultMapper.delete(new LambdaQueryWrapper<SysAiCopyResult>()
                .eq(SysAiCopyResult::getCreatorId, creatorId));
    }
}
