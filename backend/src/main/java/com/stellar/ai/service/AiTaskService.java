package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * 按任务类型分页（文本模糊 + 时间范围过滤），供跨模块历史查询（如 TTS 记录页）。
     */
    public Page<AiTask> pageByType(String taskType, String promptLike,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   int pageNum, int pageSize) {
        Page<AiTask> page = new Page<>(pageNum, pageSize);
        return aiTaskMapper.selectPage(page, new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, taskType)
                .like(StringUtils.hasText(promptLike), AiTask::getPrompt, promptLike)
                .ge(startTime != null, AiTask::getCreateTime, startTime)
                .le(endTime != null, AiTask::getCreateTime, endTime)
                .orderByDesc(AiTask::getCreateTime));
    }

    /**
     * 无归属校验的硬删除，仅供管理后台清理（用户侧删除走 delete 的归属校验）。
     */
    public void deleteById(Long id) {
        aiTaskMapper.deleteById(id);
    }

    /** 任务总量/成功数/耗时聚合（SQL 下推），供仪表盘。 */
    public Map<String, Object> taskStatTotals(String taskType, String successStatus) {
        return aiTaskMapper.selectTaskStatTotals(taskType, successStatus);
    }

    /** 今/本周/上周期数量聚合（SQL 下推），供仪表盘。 */
    public Map<String, Object> taskStatRecent(String taskType, LocalDateTime todayStart,
                                              LocalDateTime weekStart, LocalDateTime prevWeekStart) {
        return aiTaskMapper.selectTaskStatRecent(taskType, todayStart, weekStart, prevWeekStart);
    }

    /** TTS 总量/今日/总大小聚合（SQL 下推），供仪表盘。 */
    public Map<String, Object> ttsStat(LocalDateTime todayStart) {
        return aiTaskMapper.selectTtsStat(todayStart);
    }
}
