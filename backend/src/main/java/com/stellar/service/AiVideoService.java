package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.entity.SysFile;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.AiResolvedConfig;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoService {

    private final AiModelService aiModelService;
    private final SysFileMapper fileMapper;
    private final SysAiUsageService sysAiUsageService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 创建视频生成任务，返回 video_id 供前端轮询。
     */
    public AiVideoTaskVO createTask(Long modelId, String prompt, Integer width, Integer height,
                                    Integer numFrames, Double frameRate) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"VIDEO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是视频生成类型，请选择 VIDEO 类型模型");
        }

        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/videos";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.model());
            body.put("prompt", prompt);
            if (width != null) body.put("width", width);
            if (height != null) body.put("height", height);
            if (numFrames != null) body.put("num_frames", numFrames);
            if (frameRate != null) body.put("frame_rate", frameRate);
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

            // completed 时下载视频存 sys_file 永久化
            if ("completed".equals(taskStatus)) {
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
                }
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
