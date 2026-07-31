package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private final AiTaskMapper aiTaskMapper;

    public void record(AiTask task) {
        try {
            if (task.getRequestTime() == null) {
                task.setRequestTime(LocalDateTime.now());
            }
            if (task.getCreateTime() == null) {
                task.setCreateTime(LocalDateTime.now());
            }
            aiTaskMapper.insert(task);
        } catch (Exception e) {
            log.warn("记录 AI 任务历史失败: type={}, error={}", task.getTaskType(), e.getMessage(), e);
        }
    }

    public Page<AiTask> page(String taskType, String subjectType, String subjectId, int pageNum, int pageSize) {
        Page<AiTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, taskType)
                .eq(AiTask::getSubjectType, subjectType)
                .eq(AiTask::getSubjectId, subjectId)
                .orderByDesc(AiTask::getRequestTime);
        return aiTaskMapper.selectPage(page, wrapper);
    }

    public AiTask getById(Long id) {
        return aiTaskMapper.selectById(id);
    }

    public AiTask getFullById(Long id) {
        return aiTaskMapper.selectById(id);
    }

    public void delete(Long id, String subjectType, String subjectId) {
        AiTask task = aiTaskMapper.selectById(id);
        if (task == null) {
            return;
        }
        if (!subjectType.equals(task.getSubjectType()) || !subjectId.equals(task.getSubjectId())) {
            log.warn("主体 {}:{} 试图删除非自己的任务 {}", subjectType, subjectId, id);
            return;
        }
        aiTaskMapper.deleteById(id);
    }

    public void clear(String taskType, String subjectType, String subjectId) {
        aiTaskMapper.delete(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, taskType)
                .eq(AiTask::getSubjectType, subjectType)
                .eq(AiTask::getSubjectId, subjectId));
    }

    public void updateStatus(Long id, String status, Long fileId, String errorMsg) {
        AiTask task = new AiTask();
        task.setId(id);
        task.setStatus(status);
        task.setFileId(fileId);
        task.setErrorMsg(errorMsg);
        task.setResponseTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        aiTaskMapper.updateById(task);
    }
}
