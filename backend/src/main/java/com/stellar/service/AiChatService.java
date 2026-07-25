package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.ChatRequest;
import com.stellar.vo.AiResolvedConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 聊天服务：流式（SseEmitter）+ 非流式，按 modelId 解析供应商配置发起请求。
 * <p>配置解析优先级：用户自带 key（endpoint+apiKey+model）> modelId > TEXT 默认模型。
 * 请求加 stream_options.include_usage 以获取 token 用量；LLM 不返回则字符估算兜底。
 * 每次调用记录 token 消费（主体：登录按账号，游客按 IP）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;
    private final SysAiUsageService sysAiUsageService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 流式聊天（按 ChatRequest 解析配置）。
     * <p>自带 key 齐全用临时配置；否则按 modelId；都未传用 TEXT 默认模型。
     */
    public SseEmitter streamChat(ChatRequest request) {
        return doStreamChat(resolveConfig(request), request.getPrompt());
    }

    /**
     * 流式聊天（按 modelId 解析配置）。
     */
    public SseEmitter streamChat(Long modelId, String prompt) {
        return doStreamChat(aiModelService.resolveConfig(modelId), prompt);
    }

    /**
     * 非流式聊天（同步），返回完整文本。用 TEXT 默认模型。
     * <p>用于神奇海螺等需一次性拿到完整结果且不显式选模型的场景。
     */
    public String chatCompletion(String prompt) {
        return doChatCompletion(aiModelService.resolveDefaultConfig("TEXT"), prompt);
    }

    /**
     * 非流式聊天（按 modelId 解析配置）。
     */
    public String chatCompletion(Long modelId, String prompt) {
        return doChatCompletion(aiModelService.resolveConfig(modelId), prompt);
    }

    /**
     * 解析配置：自带 key 齐全 → 临时配置；否则 modelId；否则 TEXT 默认。
     */
    private AiResolvedConfig resolveConfig(ChatRequest request) {
        if (StringUtils.hasText(request.getEndpoint())
                && StringUtils.hasText(request.getApiKey())
                && StringUtils.hasText(request.getModel())) {
            // 用户自带配置（前端 localStorage 临时传入，后端不持久化）
            return new AiResolvedConfig(null, null,
                    request.getEndpoint(), request.getApiKey(), request.getModel(), "TEXT");
        }
        if (request.getModelId() != null) {
            return aiModelService.resolveConfig(request.getModelId());
        }
        return aiModelService.resolveDefaultConfig("TEXT");
    }

    private SseEmitter doStreamChat(AiResolvedConfig cfg, String prompt) {
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/chat/completions";
        String model = cfg.model();

        // 主体判断（同步阶段，request 上下文可用）
        String subjectType;
        String subjectId;
        if (StpUtil.isLogin()) {
            subjectType = "account";
            subjectId = StpUtil.getLoginIdAsString();
        } else {
            subjectType = "ip";
            subjectId = getClientIp();
        }

        SseEmitter emitter = new SseEmitter(120000L);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", true);
            // 请求 LLM 在流式末帧返回 usage（OpenAI 兼容）
            body.put("stream_options", Map.of("include_usage", true));
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 流式请求: model={}, type={}, providerId={}, promptLen={}, subject={}:{}",
                    model, cfg.modelType(), cfg.providerId(), prompt.length(), subjectType, subjectId);

            final String finalSubjectType = subjectType;
            final String finalSubjectId = subjectId;

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            sendError(emitter, "LLM 返回错误: HTTP " + response.statusCode());
                            return;
                        }
                        StringBuilder completionBuf = new StringBuilder();
                        int[] usage = {0, 0, 0};
                        boolean[] hasUsage = {false};
                        try (InputStream is = response.body()) {
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(is, StandardCharsets.UTF_8));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (!line.startsWith("data:")) continue;
                                String data = line.substring(5).trim();
                                if (data.equals("[DONE]")) break;
                                try {
                                    JsonNode json = objectMapper.readTree(data);
                                    String delta = json.path("choices").path(0)
                                            .path("delta").path("content").asText("");
                                    if (!delta.isEmpty()) {
                                        completionBuf.append(delta);
                                        emitter.send(SseEmitter.event()
                                                .data(Map.of("content", delta), MediaType.APPLICATION_JSON));
                                    }
                                    JsonNode usageNode = json.path("usage");
                                    if (!usageNode.isMissingNode() && usageNode.has("total_tokens")) {
                                        usage[0] = usageNode.path("prompt_tokens").asInt(0);
                                        usage[1] = usageNode.path("completion_tokens").asInt(0);
                                        usage[2] = usageNode.path("total_tokens").asInt(0);
                                        hasUsage[0] = true;
                                    }
                                } catch (Exception e) {
                                    log.debug("解析 LLM 响应分片失败: {}", data);
                                }
                            }
                            // 记录 token 消费
                            int promptTokens;
                            int completionTokens;
                            int totalTokens;
                            String source;
                            if (hasUsage[0]) {
                                promptTokens = usage[0];
                                completionTokens = usage[1];
                                totalTokens = usage[2];
                                source = "usage";
                            } else {
                                promptTokens = estimateTokens(prompt);
                                completionTokens = estimateTokens(completionBuf.toString());
                                totalTokens = promptTokens + completionTokens;
                                source = "estimate";
                            }
                            sysAiUsageService.record(finalSubjectType, finalSubjectId,
                                    cfg.providerId(), model, cfg.modelType(),
                                    promptTokens, completionTokens, totalTokens, source);

                            emitter.send(SseEmitter.event()
                                    .data(Map.of("done", true), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            log.info("AI 流式响应完成 tokens={}/{}/{} source={}",
                                    promptTokens, completionTokens, totalTokens, source);
                        } catch (Exception e) {
                            log.info("流式响应结束: {}", e.getMessage());
                            try {
                                emitter.complete();
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    .exceptionally(e -> {
                        log.error("调用 LLM 失败: {}", e.getMessage(), e);
                        sendError(emitter, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            throw new BusinessException("构建 AI 请求失败: " + e.getMessage());
        }

        return emitter;
    }

    private String doChatCompletion(AiResolvedConfig cfg, String prompt) {
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/chat/completions";
        String model = cfg.model();

        // 主体判断（同步阶段，request 上下文可用）
        String subjectType;
        String subjectId;
        if (StpUtil.isLogin()) {
            subjectType = "account";
            subjectId = StpUtil.getLoginIdAsString();
        } else {
            subjectType = "ip";
            subjectId = getClientIp();
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", false);
            // 海螺只需短 JSON（top-3 id），限制输出加速生成；低温更确定
            body.put("max_tokens", 100);
            body.put("temperature", 0.3);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 非流式请求: model={}, type={}, providerId={}, promptLen={}, subject={}:{}",
                    model, cfg.modelType(), cfg.providerId(), prompt.length(), subjectType, subjectId);

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("LLM 返回错误: HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0)
                    .path("message").path("content").asText("");

            // token 记录：LLM 返回 usage 用精确值，否则字符估算兜底
            JsonNode usageNode = json.path("usage");
            int promptTokens;
            int completionTokens;
            int totalTokens;
            String source;
            if (!usageNode.isMissingNode() && usageNode.has("total_tokens")) {
                promptTokens = usageNode.path("prompt_tokens").asInt(0);
                completionTokens = usageNode.path("completion_tokens").asInt(0);
                totalTokens = usageNode.path("total_tokens").asInt(0);
                source = "usage";
            } else {
                promptTokens = estimateTokens(prompt);
                completionTokens = estimateTokens(content);
                totalTokens = promptTokens + completionTokens;
                source = "estimate";
            }
            sysAiUsageService.record(subjectType, subjectId,
                    cfg.providerId(), model, cfg.modelType(),
                    promptTokens, completionTokens, totalTokens, source);

            log.info("AI 非流式响应完成 tokens={}/{}/{} source={}",
                    promptTokens, completionTokens, totalTokens, source);
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 非流式调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + e.getMessage());
        }
    }

    /**
     * 字符估算 token：中文约 1 字符≈1 token，英文 4 字符≈1 token；
     * 粗略取字符数作为保守估计（仅当 LLM 不返回 usage 时兜底）。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length();
    }

    private String getClientIp() {
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

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
