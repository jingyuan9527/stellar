package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.entity.SysAiImageTask;
import com.stellar.mapper.SysAiImageTaskMapper;
import com.stellar.vo.AiImageTaskVO;
import com.stellar.vo.AiResolvedConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * AI 图片生成服务：异步任务模式。
 * <p>createTask 立即落库返回 taskId，@Async worker 异步调供应商生成+存库，前端轮询 getTask。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiModelService aiModelService;
    private final SysAiImageTaskMapper taskMapper;
    private final AiImageTaskWorker worker;

    /**
     * 创建图片生成任务：校验模型 → 落库（generating）→ 异步生成 → 返回 taskId。
     */
    public Long createTask(Long modelId, String prompt, String size, String ratio) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"IMAGE".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是图片生成类型，请选择 IMAGE 类型模型");
        }

        SysAiImageTask task = new SysAiImageTask();
        task.setModelId(modelId);
        task.setProviderId(cfg.providerId());
        task.setSubjectType(getSubjectType());
        task.setSubjectId(getSubjectId());
        task.setPrompt(prompt);
        task.setSize(size);
        task.setRatio(ratio);
        task.setStatus("generating");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        log.info("[AI图片] 异步任务已创建 taskId={} model={} providerId={}", task.getId(), cfg.model(), cfg.providerId());
        worker.doGenerateAsync(task.getId());
        return task.getId();
    }

    /**
     * 查询任务状态。completed 时返回 /file/{id}。
     */
    public AiImageTaskVO getTask(Long taskId) {
        SysAiImageTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        AiImageTaskVO vo = new AiImageTaskVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setPrompt(task.getPrompt());
        if (task.getFileId() != null) {
            vo.setUrl("/file/" + task.getFileId());
        }
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    private String getSubjectType() {
        return StpUtil.isLogin() ? "account" : "ip";
    }

    private String getSubjectId() {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                ip = ip.split(",")[0].trim();
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
