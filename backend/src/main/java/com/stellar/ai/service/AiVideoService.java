package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.common.FileConstants;
import com.stellar.infra.SafeUrlValidator;
import com.stellar.ai.dto.AiVideoCreateDTO;
import com.stellar.ai.dto.AiVideoHistoryQueryDTO;
import com.stellar.ai.entity.AiTask;
import com.stellar.system.entity.SysFile;
import com.stellar.ai.event.VideoTaskCreatedEvent;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.ai.vo.AiVideoHistoryVO;
import com.stellar.ai.vo.AiVideoStatusVO;
import com.stellar.ai.vo.AiVideoTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.stellar.infra.ExternalCallLogger;

/**
 * AI 视频生成服务：异步任务模式（创建任务 → 后端 worker 轮询结果 → SSE 通知）。
 * <p>创建任务 POST /v1/videos 返回 video_id；查询 GET /agnesapi?video_id=xxx，
 * completed 时下载 mp4 存 sys_file 永久化。
 * <p>本地留痕：createTask 落库 ai_task(task_type=video)，getTask 被 AiVideoTaskWorker 调用更新本地行，
 * pageHistory 按主体分页查询历史。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoService {

    private final AiModelService aiModelService;
    private final SysFileMapper fileMapper;
    private final SysAiUsageService sysAiUsageService;
    private final AiTaskMapper aiTaskMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ExternalCallLogger externalCallLogger;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 创建视频生成任务，返回 video_id 供后端 worker 轮询。同时本地落库。
     */
    public AiVideoTaskVO createTask(AiVideoCreateDTO dto) {
        Long modelId = dto.getModelId();
        String prompt = dto.getPrompt();
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"VIDEO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是视频生成类型，请选择 VIDEO 类型模型");
        }

        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/videos";
        long start = System.currentTimeMillis();
        String callParams = "model=" + cfg.model() + ", providerId=" + cfg.providerId()
                + ", promptLen=" + (prompt == null ? 0 : prompt.length())
                + ", ratio=" + dto.getRatio() + ", duration=" + dto.getDuration();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.model());
            body.put("prompt", prompt);
            if (dto.getWidth() != null) body.put("width", dto.getWidth());
            if (dto.getHeight() != null) body.put("height", dto.getHeight());
            if (dto.getNumFrames() != null) body.put("num_frames", dto.getNumFrames());
            if (dto.getFrameRate() != null) body.put("frame_rate", dto.getFrameRate());
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 视频创建任务: model={}, providerId={}, promptLen={}", cfg.model(), cfg.providerId(), prompt.length());

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String respBody = response.body();
            if (status != 200) {
                log.warn("AI 视频创建失败: status={}, providerId={}, body={}", status, cfg.providerId(), respBody);
                throw new BusinessException(friendlyVideoError(status, respBody));
            }

            JsonNode json = objectMapper.readTree(respBody);
            AiVideoTaskVO vo = new AiVideoTaskVO();
            vo.setTaskId(json.path("task_id").asText(json.path("id").asText("")));
            vo.setVideoId(json.path("video_id").asText(""));
            vo.setStatus(json.path("status").asText("queued"));
            if (vo.getVideoId().isEmpty()) {
                throw new BusinessException("视频任务创建失败: 未返回 video_id");
            }

            // 记录 token 估算（视频按秒计费，此处仅记 prompt 估算供统计）
            int promptTokens = prompt.length();
            sysAiUsageService.record(getSubjectType(), getSubjectId(),
                    cfg.providerId(), cfg.model(), cfg.modelType(),
                    promptTokens, 0, promptTokens, "estimate");

            // 本地留痕（失败不影响主流程）
            try {
                AiTask task = new AiTask();
                task.setTaskType("video");
                task.setProviderId(cfg.providerId());
                task.setSubjectType(getSubjectType());
                task.setSubjectId(getSubjectId());
                task.setPrompt(prompt);
                task.setStatus("generating");
                task.setExtra(buildVideoExtra(modelId, dto.getRatio(), dto.getDuration(), vo.getVideoId()));
                task.setRequestTime(LocalDateTime.now());
                task.setCreateTime(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                aiTaskMapper.insert(task);
                eventPublisher.publishEvent(new VideoTaskCreatedEvent(task.getId(), modelId, vo.getVideoId()));
            } catch (Exception e) {
                log.warn("视频任务本地留痕失败: {}", e.getMessage(), e);
            }

            externalCallLogger.success("AI视频创建", url, callParams + ", videoId=" + vo.getVideoId(),
                    System.currentTimeMillis() - start);
            log.info("AI 视频任务已创建: videoId={}, status={}", vo.getVideoId(), vo.getStatus());
            return vo;
        } catch (BusinessException e) {
            externalCallLogger.failure("AI视频创建", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("AI视频创建", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI 视频创建异常: {}", e.getMessage(), e);
            throw new BusinessException("视频创建失败: " + e.getMessage());
        }
    }

    /**
     * 查询视频任务状态。completed 时下载 mp4 存 sys_file，返回 /file/{id}。
     * <p>同步更新本地留痕行（status/file_id/error_msg），失败不影响 worker 轮询。
     */
    public AiVideoStatusVO getTask(Long modelId, String videoId) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"VIDEO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是视频生成类型");
        }
        String encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8);
        String url = cfg.endpoint().replaceAll("/+$", "") + "/agnesapi?video_id=" + encodedVideoId;
        long start = System.currentTimeMillis();
        String callParams = "model=" + cfg.model() + ", providerId=" + cfg.providerId() + ", videoId=" + videoId;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String respBody = response.body();
            if (status != 200) {
                log.warn("AI 视频查询失败: status={}, videoId={}, body={}", status, videoId, respBody);
                throw new BusinessException(friendlyVideoError(status, respBody));
            }

            JsonNode json = objectMapper.readTree(respBody);
            String taskStatus = json.path("status").asText("unknown");
            AiVideoStatusVO vo = new AiVideoStatusVO();
            vo.setStatus(taskStatus);
            vo.setProgress(json.path("progress").asInt(0));
            vo.setSeconds(json.path("seconds").asText(""));
            vo.setSize(json.path("size").asText(""));

            // 本地留痕行（可能不存在：旧任务/留痕失败）
            AiTask local = findLocalTask(videoId);

            // completed 时下载视频存 sys_file 永久化（本地行已有 file_id 则复用，避免重复下载）
            if ("completed".equals(taskStatus)) {
                if (local != null && local.getFileId() != null) {
                    vo.setVideoUrl("/file/" + local.getFileId());
                } else {
                    String videoUrl = json.path("metadata").path("url").asText("");
                    if (StringUtils.hasText(videoUrl)) {
                        byte[] videoBytes = downloadFile(videoUrl);
                        SysFile file = new SysFile();
                        file.setOriginalName(videoId + ".mp4");
                        file.setExt("mp4");
                        file.setContentType("video/mp4");
                        file.setSize((long) videoBytes.length);
                        file.setData(videoBytes);
                        file.setCreateTime(LocalDateTime.now());
                        fileMapper.insert(file);
                        vo.setVideoUrl("/file/" + file.getId());
                        log.info("AI 视频已生成: videoId={}, fileId={}, size={}", videoId, file.getId(), videoBytes.length);
                        updateLocalCompleted(videoId, file.getId());
                    }
                }
            } else if ("failed".equals(taskStatus)) {
                updateLocalFailed(videoId);
            }
            externalCallLogger.success("AI视频查询", url, callParams + ", status=" + taskStatus,
                    System.currentTimeMillis() - start);
            return vo;
        } catch (BusinessException e) {
            externalCallLogger.failure("AI视频查询", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("AI视频查询", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI 视频查询异常: {}", e.getMessage(), e);
            throw new BusinessException("视频查询失败: " + e.getMessage());
        }
    }

    /**
     * 按主体分页查询视频生成历史（登录按账号、游客按 IP），按创建时间倒序。
     */
    public Page<AiVideoHistoryVO> pageHistory(AiVideoHistoryQueryDTO query, String subjectType, String subjectId) {
        Page<AiTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, "video")
                .eq(AiTask::getSubjectType, subjectType)
                .eq(AiTask::getSubjectId, subjectId)
                .orderByDesc(AiTask::getCreateTime);
        Page<AiTask> result = aiTaskMapper.selectPage(page, wrapper);

        Page<AiVideoHistoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toHistoryVO).toList());
        return voPage;
    }

    /**
     * 删除历史记录（校验归属，连关联文件一起删）。
     */
    public void deleteTask(Long taskId, String subjectType, String subjectId) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!subjectType.equals(task.getSubjectType()) || !subjectId.equals(task.getSubjectId())) {
            throw new BusinessException("无权删除该记录");
        }
        if (task.getFileId() != null) {
            fileMapper.deleteById(task.getFileId());
        }
        aiTaskMapper.deleteById(taskId);
        log.info("[AI视频] 删除历史记录 taskId={} fileId={}", taskId, task.getFileId());
    }

    /**
     * 校验视频任务归属当前主体（无任务行或归属不匹配时拒绝）。
     * <p>供状态查询入口防越权用：videoId 为供应商生成的全局标识，若不校验归属，
     * 任意登录用户拿他人 videoId 即可读取他人视频，且在任务完成后反过来改写他人
     * 本地留痕行（IDOR）；校验放在可外部触达的 controller 层，不影响 worker 内部轮询。
     */
    public void assertVideoOwner(String videoId, String subjectType, String subjectId) {
        AiTask task = findLocalTask(videoId);
        if (task == null) {
            throw new BusinessException("视频任务不存在");
        }
        if (!subjectType.equals(task.getSubjectType()) || !subjectId.equals(task.getSubjectId())) {
            throw new BusinessException("无权访问该视频任务");
        }
    }

    private String buildVideoExtra(Long modelId, String ratio, Integer duration, String videoId) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("model_id", modelId);
        extra.put("ratio", ratio != null ? ratio : "");
        extra.put("duration", duration);
        extra.put("video_id", videoId);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            log.warn("[AI视频] extra JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private AiTask findLocalTask(String videoId) {
        try {
            return aiTaskMapper.selectVideoTaskByVideoId(videoId);
        } catch (Exception e) {
            log.warn("查询本地视频任务失败 videoId={}: {}", videoId, e.getMessage(), e);
            return null;
        }
    }

    private void updateLocalCompleted(String videoId, Long fileId) {
        try {
            AiTask task = findLocalTask(videoId);
            if (task == null) return;
            task.setStatus("completed");
            task.setFileId(fileId);
            task.setResponseTime(LocalDateTime.now());
            if (task.getRequestTime() != null) {
                task.setDurationMs(Duration.between(task.getRequestTime(), LocalDateTime.now()).toMillis());
            }
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("更新本地视频任务完成状态失败 videoId={}: {}", videoId, e.getMessage(), e);
        }
    }

    private void updateLocalFailed(String videoId) {
        try {
            AiTask task = findLocalTask(videoId);
            if (task == null) return;
            task.setStatus("failed");
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("更新本地视频任务失败状态失败 videoId={}: {}", videoId, e.getMessage(), e);
        }
    }

    private AiVideoHistoryVO toHistoryVO(AiTask task) {
        AiVideoHistoryVO vo = new AiVideoHistoryVO();
        vo.setId(task.getId());
        vo.setPrompt(task.getPrompt());
        vo.setStatus(task.getStatus());
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
                vo.setRatio(json.has("ratio") ? json.get("ratio").asText() : null);
                vo.setDuration(json.has("duration") && !json.get("duration").isNull() ? json.get("duration").asInt() : null);
            } catch (Exception ignored) {}
        }
        return vo;
    }

    /**
     * 把供应商 HTTP 错误转成用户友好的提示。详细 body 记日志。
     */
    private String friendlyVideoError(int status, String body) {
        switch (status) {
            case 502:
            case 503:
            case 504:
                return "视频生成服务繁忙，请稍后重试";
            case 429:
                return "请求过于频繁，请稍后重试";
            case 401:
            case 403:
                return "API Key 无效或无权限，请检查供应商配置";
            case 400:
                return "视频生成请求参数有误: " + extractErrorMessage(body);
            default:
                return "视频生成失败: HTTP " + status;
        }
    }

    /** 从供应商错误响应体提取 error.message。 */
    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String msg = node.path("error").path("message").asText("");
            if (StringUtils.hasText(msg)) {
                return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
            }
        } catch (Exception ignored) {
        }
        String trimmed = body.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }

    /**
     * 下载视频 URL 为字节数组，落库永久化。
     */
    private byte[] downloadFile(String url) {
        try {
            URI uri = SafeUrlValidator.validatePublicHttpUrl(url, "视频下载地址");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMinutes(3))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                throw new BusinessException("下载视频失败: HTTP " + resp.statusCode());
            }
            long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > FileConstants.GENERATED_VIDEO_MAX_BYTES) {
                resp.body().close();
                throw new BusinessException("下载视频超过大小限制");
            }
            try (InputStream input = resp.body()) {
                return SafeUrlValidator.readLimited(input, FileConstants.GENERATED_VIDEO_MAX_BYTES, "下载视频");
            }
        } catch (BusinessException e) {
            log.warn("AI 视频下载被拒绝 url={} reason={}", url, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("AI 视频下载异常 url={}: {}", url, e.getMessage(), e);
            throw new BusinessException("下载视频失败: " + e.getMessage());
        }
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
