package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.common.FileConstants;
import com.stellar.infra.SafeUrlValidator;
import com.stellar.ai.entity.AiTask;
import com.stellar.system.entity.SysFile;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.ai.vo.AiResolvedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import com.stellar.infra.ExternalCallLogger;

/**
 * AI 图片生成异步 worker：@Async 线程调供应商生成 + 下载存库 + 更新任务记录。
 * <p>与 AiImageService 分离，避免 @Async 自调用失效（Spring AOP 代理限制）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageTaskWorker {

    private final AiModelService aiModelService;
    private final AiTaskMapper aiTaskMapper;
    private final SysFileMapper fileMapper;
    private final SysAiUsageService sysAiUsageService;
    private final ObjectMapper objectMapper;
    private final AiNotifyPublisher publisher;
    private final ExternalCallLogger externalCallLogger;
    private final PlatformTransactionManager transactionManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 异步生成图片：调供应商 → 下载/解码 → 存 sys_file → 更新任务记录。
     */
    @Async("aiTaskExecutor")
    public void doGenerateAsync(Long taskId) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[AI图片] 异步任务记录不存在 taskId={}", taskId);
            return;
        }
        try {
            Long modelId = null;
            String size = null;
            String ratio = null;
            if (task.getExtra() != null) {
                try {
                    var json = objectMapper.readTree(task.getExtra());
                    modelId = json.has("model_id") ? json.get("model_id").asLong() : null;
                    size = json.has("size") ? json.get("size").asText() : null;
                    ratio = json.has("ratio") ? json.get("ratio").asText() : null;
                } catch (Exception ignored) {}
            }
            AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
            log.info("[AI图片] 异步生成开始 taskId={} model={}", taskId, cfg.model());
            byte[] imageBytes = generateImageBytes(cfg, task.getPrompt(), size, ratio);

            // 文件落库 + 任务状态更新包进同一事务（@Async 方法内用编程式事务，避免自调用失效）：
            // 任一步失败整段回滚，杜绝"孤儿文件或任务永不终态"的中间态。
            Long fileId = new TransactionTemplate(transactionManager).execute(tx -> {
                SysFile file = new SysFile();
                String name = task.getPrompt().length() > 20 ? task.getPrompt().substring(0, 20) : task.getPrompt();
                file.setOriginalName(name + ".png");
                file.setExt("png");
                file.setContentType("image/png");
                file.setSize((long) imageBytes.length);
                file.setData(imageBytes);
                file.setCreateTime(LocalDateTime.now());
                fileMapper.insert(file);

                task.setStatus("completed");
                task.setFileId(file.getId());
                task.setResponseTime(LocalDateTime.now());
                task.setDurationMs(Duration.between(task.getRequestTime(), LocalDateTime.now()).toMillis());
                task.setUpdateTime(LocalDateTime.now());
                aiTaskMapper.updateById(task);
                return file.getId();
            });

            int promptTokens = task.getPrompt().length();
            sysAiUsageService.record(task.getSubjectType(), task.getSubjectId(),
                    cfg.providerId(), cfg.model(), cfg.modelType(),
                    promptTokens, 0, promptTokens, "estimate");

            publisher.publish(new AiNotifyMessage(
                    task.getSubjectType() + ":" + task.getSubjectId(),
                    "image", taskId, "completed"));
            log.info("[AI图片] 异步生成完成 taskId={} fileId={} size={}", taskId, fileId, imageBytes.length);
        } catch (Exception e) {
            log.error("[AI图片] 异步生成失败 taskId={}: {}", taskId, e.getMessage(), e);
            markFailed(taskId, e instanceof BusinessException ? e.getMessage() : "图片生成失败: " + e.getMessage());
            publisher.publish(new AiNotifyMessage(
                    task.getSubjectType() + ":" + task.getSubjectId(),
                    "image", taskId, "failed"));
        }
    }

    /**
     * 调供应商图片生成接口，返回图片字节数组（b64 解码或 url 下载）。
     * <p>由 {@link AiImageService#generateImageSync} 同步调用（聊天工具调用路径），
     * 亦由 {@link #doGenerateAsync} 异步调用（图片页路径）。
     */
    public byte[] generateImageBytes(AiResolvedConfig cfg, String prompt, String size, String ratio) throws Exception {
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/images/generations";
        String sz = StringUtils.hasText(size) ? size : "1K";
        String rt = StringUtils.hasText(ratio) ? ratio : "1:1";
        long start = System.currentTimeMillis();
        String callParams = "model=" + cfg.model() + ", providerId=" + cfg.providerId()
                + ", size=" + sz + ", ratio=" + rt + ", promptLen=" + (prompt == null ? 0 : prompt.length());

        Map<String, Object> body = new HashMap<>();
        body.put("model", cfg.model());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", sz);
        body.put("ratio", rt);
        String bodyJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String respBody = response.body();
            if (status != 200) {
                log.warn("AI 图片生成失败: status={}, providerId={}, body={}", status, cfg.providerId(), respBody);
                throw new BusinessException(friendlyImageError(status, respBody));
            }

            JsonNode json = objectMapper.readTree(respBody);
            JsonNode dataNode = json.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                throw new BusinessException("图片生成失败: 返回数据为空");
            }
            String b64 = dataNode.path(0).path("b64_json").asText("");
            byte[] bytes;
            if (StringUtils.hasText(b64)) {
                long maxEncodedLength = ((FileConstants.GENERATED_IMAGE_MAX_BYTES + 2) / 3) * 4;
                if (b64.length() > maxEncodedLength) {
                    throw new BusinessException("生成图片超过大小限制");
                }
                bytes = Base64.getDecoder().decode(b64);
                if (bytes.length > FileConstants.GENERATED_IMAGE_MAX_BYTES) {
                    throw new BusinessException("生成图片超过大小限制");
                }
            } else {
                String imgUrl = dataNode.path(0).path("url").asText("");
                if (!StringUtils.hasText(imgUrl)) {
                    throw new BusinessException("图片生成失败: 未返回图片数据");
                }
                bytes = downloadFile(imgUrl);
                log.info("[AI图片] 供应商返回 URL，已下载 {} 字节", bytes.length);
            }
            externalCallLogger.success("AI图片", url, callParams + ", resultBytes=" + bytes.length,
                    System.currentTimeMillis() - start);
            return bytes;
        } catch (Exception e) {
            externalCallLogger.failure("AI图片", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void markFailed(Long taskId, String msg) {
        try {
            AiTask task = aiTaskMapper.selectById(taskId);
            if (task == null) return;
            task.setStatus("failed");
            task.setErrorMsg(msg);
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("[AI图片] 标记失败状态异常 taskId={}: {}", taskId, e.getMessage(), e);
        }
    }

    private String friendlyImageError(int status, String body) {
        switch (status) {
            case 502:
            case 503:
            case 504:
                return "图片生成服务繁忙，请稍后重试";
            case 429:
                return "请求过于频繁，请稍后重试";
            case 401:
            case 403:
                return "API Key 无效或无权限，请检查供应商配置";
            case 400:
                return "图片生成请求参数有误: " + extractErrorMessage(body);
            default:
                return "图片生成失败: HTTP " + status;
        }
    }

    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) return "";
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

    private byte[] downloadFile(String url) {
        try {
            URI uri = SafeUrlValidator.validatePublicHttpUrl(url, "图片下载地址");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                throw new BusinessException("下载图片失败: HTTP " + resp.statusCode());
            }
            long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > FileConstants.GENERATED_IMAGE_MAX_BYTES) {
                resp.body().close();
                throw new BusinessException("下载图片超过大小限制");
            }
            try (InputStream input = resp.body()) {
                return SafeUrlValidator.readLimited(input, FileConstants.GENERATED_IMAGE_MAX_BYTES, "下载图片");
            }
        } catch (BusinessException e) {
            log.warn("[AI图片] 下载被拒绝 url={} reason={}", url, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[AI图片] 下载异常 url={}: {}", url, e.getMessage(), e);
            throw new BusinessException("下载图片失败: " + e.getMessage());
        }
    }
}
