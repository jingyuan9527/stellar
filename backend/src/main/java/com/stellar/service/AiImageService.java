package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.dto.AiImageHistoryQueryDTO;
import com.stellar.entity.SysAiImageTask;
import com.stellar.entity.SysFile;
import com.stellar.mapper.SysAiImageTaskMapper;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.AiImageTaskVO;
import com.stellar.vo.AiResolvedConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * AI 图片生成服务：异步任务模式。
 * <p>createTask 立即落库返回 taskId，@Async worker 异步调供应商生成+存库，前端 SSE 通知完成（getTask 保留兜底查询）。
 * <p>pageHistory 按主体（account/ip）分页查询历史，复用 sys_ai_image_task。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiModelService aiModelService;
    private final SysAiImageTaskMapper taskMapper;
    private final SysFileMapper fileMapper;
    private final AiImageTaskWorker worker;
    private final SysAiUsageService sysAiUsageService;

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
     * 同步生成图片（聊天工具调用路径）：调供应商生成 → 存 sys_file → 写 sys_ai_image_task(completed) → 返回 fileId。
     * <p>与 {@link #createTask} 异步任务模式不同，本方法同步阻塞等待生成完成
     * （在 SSE 流式回调线程跑，不阻塞 servlet 线程）。
     * <p>使用 IMAGE 默认模型 + size=1K + ratio=1:1 固定参数（prompt 由 LLM 决定）。
     * <p>不发 SSE 通知（聊天页有自己的进度提示，避免图片页重复弹抽屉）。
     *
     * @param prompt 图片提示词
     * @return sys_file.id（用于挂 ai_chat_message.attachment_file_id）
     * @throws BusinessException 生成失败（task 已标 failed，异常向上抛由 ToolService 捕获）
     */
    public Long generateImageSync(String prompt) {
        AiResolvedConfig cfg = aiModelService.resolveDefaultOrFirstEnabled("IMAGE");
        String subjectType = getSubjectType();
        String subjectId = getSubjectId();

        SysAiImageTask task = new SysAiImageTask();
        task.setModelId(cfg.modelId());
        task.setProviderId(cfg.providerId());
        task.setSubjectType(subjectType);
        task.setSubjectId(subjectId);
        task.setPrompt(prompt);
        task.setSize("1K");
        task.setRatio("1:1");
        task.setStatus("generating");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

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
            fileMapper.insert(file);

            task.setStatus("completed");
            task.setFileId(file.getId());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

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
            taskMapper.updateById(task);
            throw e instanceof BusinessException be ? be : new BusinessException("图片生成失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务状态。completed 时返回 /file/{id}。
     */
    public AiImageTaskVO getTask(Long taskId) {
        SysAiImageTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return toVO(task);
    }

    /**
     * 按主体分页查询图片生成历史（登录按账号、游客按 IP），按创建时间倒序。
     */
    public Page<AiImageTaskVO> pageHistory(AiImageHistoryQueryDTO query, String subjectType, String subjectId) {
        Page<SysAiImageTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysAiImageTask> wrapper = new LambdaQueryWrapper<SysAiImageTask>()
                .eq(SysAiImageTask::getSubjectType, subjectType)
                .eq(SysAiImageTask::getSubjectId, subjectId)
                .orderByDesc(SysAiImageTask::getCreateTime);
        Page<SysAiImageTask> result = taskMapper.selectPage(page, wrapper);

        Page<AiImageTaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 删除历史记录（校验归属，连关联文件一起删）。
     */
    public void deleteTask(Long taskId, String subjectType, String subjectId) {
        SysAiImageTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!subjectType.equals(task.getSubjectType()) || !subjectId.equals(task.getSubjectId())) {
            throw new BusinessException("无权删除该记录");
        }
        if (task.getFileId() != null) {
            fileMapper.deleteById(task.getFileId());
        }
        taskMapper.deleteById(taskId);
        log.info("[AI图片] 删除历史记录 taskId={} fileId={}", taskId, task.getFileId());
    }

    private AiImageTaskVO toVO(SysAiImageTask task) {
        AiImageTaskVO vo = new AiImageTaskVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setPrompt(task.getPrompt());
        vo.setSize(task.getSize());
        vo.setRatio(task.getRatio());
        if (task.getFileId() != null) {
            vo.setUrl("/file/" + task.getFileId());
        }
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        if (task.getUpdateTime() != null && task.getCreateTime() != null
                && task.getUpdateTime().isAfter(task.getCreateTime())) {
            vo.setDurationMs(Duration.between(task.getCreateTime(), task.getUpdateTime()).toMillis());
        }
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
