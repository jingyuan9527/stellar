package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiImageHistoryQueryDTO;
import com.stellar.ai.entity.AiTask;
import com.stellar.system.entity.SysFile;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.vo.AiImageTaskVO;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.interceptor.WebUtils;
import com.stellar.system.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiModelService aiModelService;
    private final AiTaskMapper aiTaskMapper;
    private final FileService fileService;
    private final AiImageTaskWorker worker;
    private final SysAiUsageService sysAiUsageService;
    private final ObjectMapper objectMapper;

    public Long createTask(Long modelId, String prompt, String size, String ratio) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"IMAGE".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是图片生成类型，请选择 IMAGE 类型模型");
        }

        AiTask task = new AiTask();
        task.setTaskType("image");
        task.setProviderId(cfg.providerId());
        task.setSubjectType(getSubjectType());
        task.setSubjectId(getSubjectId());
        task.setPrompt(prompt);
        task.setStatus("generating");
        task.setExtra(buildExtra(modelId, size != null ? size : "", ratio != null ? ratio : ""));
        task.setRequestTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        aiTaskMapper.insert(task);

        log.info("[AI图片] 异步任务已创建 taskId={} model={} providerId={}", task.getId(), cfg.model(), cfg.providerId());
        worker.doGenerateAsync(task.getId());
        return task.getId();
    }

    public Long generateImageSync(String prompt, String subjectType, String subjectId) {
        AiResolvedConfig cfg = aiModelService.resolveDefaultOrFirstEnabled("IMAGE");

        AiTask task = new AiTask();
        task.setTaskType("image");
        task.setProviderId(cfg.providerId());
        task.setSubjectType(subjectType);
        task.setSubjectId(subjectId);
        task.setPrompt(prompt);
        task.setStatus("generating");
        task.setExtra(buildExtra(cfg.modelId(), "1K", "1:1"));
        task.setRequestTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        aiTaskMapper.insert(task);

        try {
            log.info("[AI图片] 聊天工具同步生成开始 taskId={} model={}", task.getId(), cfg.model());
            byte[] imageBytes = worker.generateImageBytes(cfg, prompt, "1K", "1:1");

            SysFile file = new SysFile();
            String name = prompt.length() > 20 ? prompt.substring(0, 20) : prompt;
            file.setOriginalName(name + ".png");
            file.setExt("png");
            file.setContentType("image/png");
            file.setSize((long) imageBytes.length);
            file.setData(imageBytes);
            file.setCreateTime(LocalDateTime.now());
            fileService.create(file);

            task.setStatus("completed");
            task.setFileId(file.getId());
            task.setResponseTime(LocalDateTime.now());
            task.setDurationMs(Duration.between(task.getRequestTime(), LocalDateTime.now()).toMillis());
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);

            int promptTokens = prompt.length();
            sysAiUsageService.record(subjectType, subjectId,
                    cfg.providerId(), cfg.model(), cfg.modelType(),
                    promptTokens, 0, promptTokens, "estimate");

            log.info("[AI图片] 聊天工具同步生成完成 taskId={} fileId={} size={}", task.getId(), file.getId(), imageBytes.length);
            return file.getId();
        } catch (Exception e) {
            log.error("[AI图片] 聊天工具同步生成失败 taskId={}: {}", task.getId(), e.getMessage(), e);
            task.setStatus("failed");
            task.setErrorMsg(e instanceof BusinessException ? e.getMessage() : "图片生成失败: " + e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
            throw e instanceof BusinessException be ? be : new BusinessException("图片生成失败: " + e.getMessage());
        }
    }

    public AiImageTaskVO getTask(Long taskId) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return toVO(task);
    }

    public Page<AiImageTaskVO> pageHistory(AiImageHistoryQueryDTO query, String subjectType, String subjectId) {
        Page<AiTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, "image")
                .eq(AiTask::getSubjectType, subjectType)
                .eq(AiTask::getSubjectId, subjectId)
                .orderByDesc(AiTask::getCreateTime);
        Page<AiTask> result = aiTaskMapper.selectPage(page, wrapper);

        Page<AiImageTaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    public void deleteTask(Long taskId, String subjectType, String subjectId) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!subjectType.equals(task.getSubjectType()) || !subjectId.equals(task.getSubjectId())) {
            throw new BusinessException("无权删除该记录");
        }
        if (task.getFileId() != null) {
            fileService.deleteById(task.getFileId());
        }
        aiTaskMapper.deleteById(taskId);
        log.info("[AI图片] 删除历史记录 taskId={} fileId={}", taskId, task.getFileId());
    }

    private AiImageTaskVO toVO(AiTask task) {
        AiImageTaskVO vo = new AiImageTaskVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setPrompt(task.getPrompt());
        if (task.getFileId() != null) {
            vo.setUrl("/file/" + task.getFileId());
        }
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        vo.setDurationMs(task.getDurationMs());
        if (task.getExtra() != null) {
            try {
                var json = objectMapper.readTree(task.getExtra());
                vo.setSize(json.has("size") ? json.get("size").asText() : null);
                vo.setRatio(json.has("ratio") ? json.get("ratio").asText() : null);
            } catch (Exception ignored) {}
        }
        return vo;
    }

    private String buildExtra(Long modelId, String size, String ratio) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("model_id", modelId);
        extra.put("size", size);
        extra.put("ratio", ratio);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            log.warn("[AI图片] extra JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String getSubjectType() {
        return StpUtil.isLogin() ? "account" : "ip";
    }

    private String getSubjectId() {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        return WebUtils.getClientIp();
    }
}
