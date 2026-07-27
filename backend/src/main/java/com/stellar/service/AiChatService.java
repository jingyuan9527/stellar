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
import java.time.LocalDateTime;
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
    private final SysAiChatRecordService sysAiChatRecordService;

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
            // 历史落库用：请求时刻、供应商/模型（lambda 内 final 捕获）
            final LocalDateTime requestTime = LocalDateTime.now();
            final long requestTimeMillis = System.currentTimeMillis();
            final Long providerId = cfg.providerId();
            final String finalModel = model;
            final boolean[] recorded = {false};

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            String errMsg = "LLM 返回错误: HTTP " + response.statusCode();
                            recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, null, "failed", errMsg, requestTime, requestTimeMillis);
                            sendError(emitter, errMsg);
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

                            recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, completionBuf.toString(), "success", null,
                                    requestTime, requestTimeMillis);

                            emitter.send(SseEmitter.event()
                                    .data(Map.of("done", true), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            log.info("AI 流式响应完成 tokens={}/{}/{} source={}",
                                    promptTokens, completionTokens, totalTokens, source);
                        } catch (Exception e) {
                            log.info("流式响应结束: {}", e.getMessage());
                            recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, completionBuf.toString(), "failed", e.getMessage(),
                                    requestTime, requestTimeMillis);
                            try {
                                emitter.complete();
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    .exceptionally(e -> {
                        log.error("调用 LLM 失败: {}", e.getMessage(), e);
                        recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                prompt, null, "failed", e.getMessage(), requestTime, requestTimeMillis);
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

    /**
     * 落库一次文本生成历史（幂等守卫：每条请求只记一次，避免异常路径重复落库）。
     * <p>历史落库异常仅记日志，不影响流式主流程。
     */
    private void recordHistory(boolean[] recorded, String subjectType, String subjectId,
                               Long providerId, String model, String prompt, String result,
                               String status, String errorMsg,
                               LocalDateTime requestTime, long requestTimeMillis) {
        if (recorded[0]) {
            return;
        }
        recorded[0] = true;
        LocalDateTime responseTime = LocalDateTime.now();
        long durationMs = System.currentTimeMillis() - requestTimeMillis;
        sysAiChatRecordService.record(subjectType, subjectId, providerId, model,
                prompt, result, status, errorMsg, requestTime, responseTime, durationMs);
    }

    // ===== 多轮聊天（AI 聊天模块用）=====
    // 不落 sys_ai_chat_record（扁平单轮表）；消息历史由 AiChatSessionService 落 ai_chat_message。
    // 仅记 token usage（计费）。messages 为 OpenAI 格式 [{role,content}]，含 system/user/assistant。

    /**
     * 多轮流式聊天。modelId 为空用 TEXT 默认模型。仅记 token，不落历史。
     */
    public SseEmitter streamMultiChat(List<Map<String, String>> messages, Long modelId) {
        return streamMultiChat(messages, modelId, null);
    }

    /**
     * 多轮流式聊天（带完成回调）。流式成功结束时回调 onComplete(完整文本)，供调用方落消息表。
     * <p>回调异常被吞掉，不影响流式主流程。
     */
    public SseEmitter streamMultiChat(List<Map<String, String>> messages, Long modelId,
                                      java.util.function.Consumer<String> onComplete) {
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doStreamChatMulti(messages, cfg, onComplete);
    }

    /**
     * 非流式多消息（system+user 等），不限制 max_tokens，记 token，不落历史。
     * <p>供长期记忆摘要等需一次性完整结果且带 system 指令的场景用。
     */
    public String chatCompletionWithMessages(List<Map<String, String>> messages, Long modelId) {
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doChatCompletionMessages(messages, cfg);
    }

    private SseEmitter doStreamChatMulti(List<Map<String, String>> messages, AiResolvedConfig cfg,
                                         java.util.function.Consumer<String> onComplete) {
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/chat/completions";
        String model = cfg.model();

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
            body.put("messages", messages);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 多轮流式: model={}, msgCount={}, subject={}:{}", model, messages.size(), subjectType, subjectId);

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            sendError(emitter, "LLM 返回错误: HTTP " + response.statusCode());
                            return;
                        }
                        StringBuilder buf = new StringBuilder();
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
                                        buf.append(delta);
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
                            recordUsage(cfg, model, messages, buf.toString(), hasUsage[0], usage);
                            if (onComplete != null) {
                                try {
                                    onComplete.accept(buf.toString());
                                } catch (Exception ce) {
                                    log.warn("多轮流式 onComplete 回调失败（不影响流式）: {}", ce.getMessage());
                                }
                            }
                            emitter.send(SseEmitter.event().data(Map.of("done", true), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            log.info("AI 多轮流式完成 tokens={}/{}/{}", usage[0], usage[1], usage[2]);
                        } catch (Exception e) {
                            log.info("多轮流式响应结束: {}", e.getMessage());
                            try { emitter.complete(); } catch (Exception ignored) {}
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

    private String doChatCompletionMessages(List<Map<String, String>> messages, AiResolvedConfig cfg) {
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/chat/completions";
        String model = cfg.model();
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
            body.put("messages", messages);
            body.put("stream", false);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 非流式多消息: model={}, msgCount={}, subject={}:{}", model, messages.size(), subjectType, subjectId);
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("LLM 返回错误: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usageNode = json.path("usage");
            int[] usage = {0, 0, 0};
            boolean hasUsage = false;
            if (!usageNode.isMissingNode() && usageNode.has("total_tokens")) {
                usage[0] = usageNode.path("prompt_tokens").asInt(0);
                usage[1] = usageNode.path("completion_tokens").asInt(0);
                usage[2] = usageNode.path("total_tokens").asInt(0);
                hasUsage = true;
            }
            recordUsage(cfg, model, messages, content, hasUsage, usage);
            log.info("AI 非流式多消息完成 tokens={}/{}/{}", usage[0], usage[1], usage[2]);
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 非流式多消息调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + e.getMessage());
        }
    }

    /**
     * 记录 token 消费（多轮场景无单条 prompt，prompt 字段记简要摘要）。
     */
    private void recordUsage(AiResolvedConfig cfg, String model, List<Map<String, String>> messages,
                             String result, boolean hasUsage, int[] usage) {
        int promptTokens;
        int completionTokens;
        int totalTokens;
        String source;
        if (hasUsage) {
            promptTokens = usage[0];
            completionTokens = usage[1];
            totalTokens = usage[2];
            source = "usage";
        } else {
            int promptLen = messages.stream()
                    .mapToInt(m -> m.get("content") == null ? 0 : m.get("content").length())
                    .sum();
            promptTokens = estimateTokens(String.valueOf(promptLen));
            completionTokens = estimateTokens(result);
            totalTokens = promptTokens + completionTokens;
            source = "estimate";
        }
        try {
            String subjectType;
            String subjectId;
            if (StpUtil.isLogin()) {
                subjectType = "account";
                subjectId = StpUtil.getLoginIdAsString();
            } else {
                subjectType = "ip";
                subjectId = getClientIp();
            }
            sysAiUsageService.record(subjectType, subjectId, cfg.providerId(), model, cfg.modelType(),
                    promptTokens, completionTokens, totalTokens, source);
        } catch (Exception e) {
            log.warn("记录 token usage 失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
