package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.dto.AiChatRecordQueryDTO;
import com.stellar.entity.SysAiChatRecord;
import com.stellar.mapper.SysAiChatRecordMapper;
import com.stellar.vo.AiChatRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 文本生成历史服务：流式结束自动落库 + 按主体（account/ip）分页查询。
 * <p>落库由 AiChatService 在流式结束时调用，异常吞掉以免影响流式响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiChatRecordService {

    private final SysAiChatRecordMapper chatRecordMapper;

    /**
     * 记录一次文本生成。异常仅记日志，不抛出（历史落库不得影响主流程）。
     */
    public void record(String subjectType, String subjectId, Long providerId, String model,
                       String prompt, String result, String status, String errorMsg,
                       LocalDateTime requestTime, LocalDateTime responseTime, Long durationMs) {
        try {
            SysAiChatRecord record = new SysAiChatRecord();
            record.setSubjectType(subjectType);
            record.setSubjectId(subjectId);
            record.setProviderId(providerId);
            record.setModel(model);
            record.setPrompt(prompt);
            record.setResult(result);
            record.setStatus(status);
            record.setErrorMsg(errorMsg);
            record.setRequestTime(requestTime);
            record.setResponseTime(responseTime);
            record.setDurationMs(durationMs);
            record.setCreateTime(LocalDateTime.now());
            chatRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("记录 AI 文本生成历史失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 按主体分页查询历史（登录按账号、游客按 IP），按请求时间倒序。
     */
    public Page<AiChatRecordVO> page(AiChatRecordQueryDTO query, String subjectType, String subjectId) {
        Page<SysAiChatRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysAiChatRecord> wrapper = new LambdaQueryWrapper<SysAiChatRecord>()
                .eq(SysAiChatRecord::getSubjectType, subjectType)
                .eq(SysAiChatRecord::getSubjectId, subjectId)
                .orderByDesc(SysAiChatRecord::getRequestTime);
        Page<SysAiChatRecord> result = chatRecordMapper.selectPage(page, wrapper);

        Page<AiChatRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 删除单条历史（校验主体归属，游客按 IP、登录按账号）。
     */
    public void delete(Long id, String subjectType, String subjectId) {
        SysAiChatRecord record = chatRecordMapper.selectById(id);
        if (record == null) {
            return;
        }
        if (!subjectType.equals(record.getSubjectType()) || !subjectId.equals(record.getSubjectId())) {
            log.warn("主体 {}:{} 试图删除非自己的文本生成历史 {}", subjectType, subjectId, id);
            return;
        }
        chatRecordMapper.deleteById(id);
    }

    /**
     * 清空当前主体的全部历史。
     */
    public void clear(String subjectType, String subjectId) {
        chatRecordMapper.delete(new LambdaQueryWrapper<SysAiChatRecord>()
                .eq(SysAiChatRecord::getSubjectType, subjectType)
                .eq(SysAiChatRecord::getSubjectId, subjectId));
    }

    private AiChatRecordVO toVO(SysAiChatRecord record) {
        AiChatRecordVO vo = new AiChatRecordVO();
        vo.setId(record.getId());
        vo.setModel(record.getModel());
        vo.setPrompt(record.getPrompt());
        vo.setResult(record.getResult());
        vo.setStatus(record.getStatus());
        vo.setErrorMsg(record.getErrorMsg());
        vo.setRequestTime(record.getRequestTime());
        vo.setResponseTime(record.getResponseTime());
        vo.setDurationMs(record.getDurationMs());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}
