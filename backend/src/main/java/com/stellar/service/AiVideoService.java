package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.AiVideoCreateDTO;
import com.stellar.dto.AiVideoHistoryQueryDTO;
import com.stellar.entity.SysAiVideoTask;
import com.stellar.entity.SysFile;
import com.stellar.mapper.SysAiVideoTaskMapper;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.AiResolvedConfig;
import com.stellar.vo.AiVideoHistoryVO;
import com.stellar.vo.AiVideoStatusVO;
import com.stellar.vo.AiVideoTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 视频生成服务：异步任务模式（创建任务 → 轮询结果）。
 * <p>创建任务 POST /v1/videos 返回 video_id；查询 GET /agnesapi?video_id=xxx，
 * completed 时下载 mp4 存 sys_file 永久化。
 * <p>本地留痕：createTask 落库 sys_ai_video_task，getTask 轮询更新本地行，
 * pageHistory 按主体分页查询历史。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoService {

    private final AiModelService aiModelService;
    private final SysFileMapper fileMapper;
    private final SysAiUsageService sysAiUsageService;
    private final SysAiVideoTaskMapper videoTaskMapper;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 创建视频生成任务，返回 video_id 供前端轮询。同时本地落库留痕。
     */
    public AiVideoTaskVO createTask(AiVideoCreateDTO dto) {
        Long modelId = dto.getModelId();
        String prompt = dto.getPrompt();
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"VIDEO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是视频生成类型，请选择 VIDEO 类型模型");
        }

        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/videos";
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
                SysAiVideoTask task = new SysAiVideoTask();
                task.setModelId(modelId);
                task.setProviderId(cfg.providerId());
                task.setSubjectType(getSubjectType());
                task.setSubjectId(getSubjectId());
                task.setPrompt(prompt);
                task.setRatio(dto.getRatio());
                task.setDuration(dto.getDuration());
                task.setWidth(dto.getWidth());
                task.setHeight(dto.getHeight());
                task.setNumFrames(dto.getNumFrames());
                task.setFrameRate(dto.getFrameRate());
                task.setVideoId(vo.getVideoId());
                task.setStatus("generating");
                task.setCreateTime(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                videoTaskMapper.insert(task);
            } catch (Exception e) {
                log.warn("视频任务本地留痕失败: {}", e.getMessage());
            }

            log.info("AI 视频任务已创建: videoId={}, status={}", vo.getVideoId(), vo.getStatus());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 视频创建异常: {}", e.getMessage(), e);
            throw new BusinessException("视频创建失败: " + e.getMessage());
        }
    }

    /**
     * 查询视频任务状态。completed 时下载 mp4 存 sys_file，返回 /file/{id}。
     * <p>同步更新本地留痕行（status/file_id/error_msg），失败不影响轮询。
     */
    public AiVideoStatusVO getTask(Long modelId, String videoId) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"VIDEO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是视频生成类型");
        }
        String url = cfg.endpoint().replaceAll("/+$", "") + "/agnesapi?video_id=" + videoId;
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
            SysAiVideoTask local = findLocalTask(videoId);

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
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 视频查询异常: {}", e.getMessage(), e);
            throw new BusinessException("视频查询失败: " + e.getMessage());
        }
    }

    /**
     * 按主体分页查询视频生成历史（登录按账号、游客按 IP），按创建时间倒序。
     */
    public Page<AiVideoHistoryVO> pageHistory(AiVideoHistoryQueryDTO query, String subjectType, String subjectId) {
        Page<SysAiVideoTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysAiVideoTask> wrapper = new LambdaQueryWrapper<SysAiVideoTask>()
                .eq(SysAiVideoTask::getSubjectType, subjectType)
                .eq(SysAiVideoTask::getSubjectId, subjectId)
                .orderByDesc(SysAiVideoTask::getCreateTime);
        Page<SysAiVideoTask> result = videoTaskMapper.selectPage(page, wrapper);

        Page<AiVideoHistoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toHistoryVO).toList());
        return voPage;
    }

    private SysAiVideoTask findLocalTask(String videoId) {
        try {
            return videoTaskMapper.selectOne(new LambdaQueryWrapper<SysAiVideoTask>()
                    .eq(SysAiVideoTask::getVideoId, videoId));
        } catch (Exception e) {
            log.warn("查询本地视频任务失败 videoId={}: {}", videoId, e.getMessage());
            return null;
        }
    }

    private void updateLocalCompleted(String videoId, Long fileId) {
        try {
            SysAiVideoTask task = findLocalTask(videoId);
            if (task == null) return;
            task.setStatus("completed");
            task.setFileId(fileId);
            task.setUpdateTime(LocalDateTime.now());
            videoTaskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("更新本地视频任务完成状态失败 videoId={}: {}", videoId, e.getMessage());
        }
    }

    private void updateLocalFailed(String videoId) {
        try {
            SysAiVideoTask task = findLocalTask(videoId);
            if (task == null) return;
            task.setStatus("failed");
            task.setUpdateTime(LocalDateTime.now());
            videoTaskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("更新本地视频任务失败状态失败 videoId={}: {}", videoId, e.getMessage());
        }
    }

    private AiVideoHistoryVO toHistoryVO(SysAiVideoTask task) {
        AiVideoHistoryVO vo = new AiVideoHistoryVO();
        vo.setId(task.getId());
        vo.setPrompt(task.getPrompt());
        vo.setRatio(task.getRatio());
        vo.setDuration(task.getDuration());
        vo.setStatus(task.getStatus());
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
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new BusinessException("下载视频失败: HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
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
