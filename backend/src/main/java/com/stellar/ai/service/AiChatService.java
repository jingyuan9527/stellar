package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.ChatRequest;
import com.stellar.interceptor.WebUtils;
import com.stellar.ai.vo.AiChatResult;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.ai.vo.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.infra.SafeUrlValidator;
import com.stellar.ai.protocol.LlmChatClient;
import jakarta.annotation.Resource;

/**
 * AI 聊天服务：流式（SseEmitter）+ 非流式，按 modelId 解析供应商配置发起请求。
 * <p>配置解析优先级：用户自带 key（endpoint+apiKey+model）> modelId > TEXT 默认模型。
 * 请求加 stream_options.include_usage 以获取 token 用量；LLM 不返回则字符估算兜底。
 * 每次调用记录 token 消费（主体：登录按账号，游客按 IP）。
 * <p>关注点拆分：本类只做编排（配置解析/SSE 生命周期/流式循环/工具二段流）；
 * token 计费与历史落库在 {@link AiUsageRecorder}，SSE 写通道在 {@link SseEmitterChannel}，
 * 传输层（URL 拼接/HTTP 发送/流解析）在此，待 A7 协议抽象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;
    private final AiUsageRecorder aiUsageRecorder;
    private final AiChatToolService aiChatToolService;
    private final ExternalCallLogger externalCallLogger;
    private final LlmChatClient llmClient;

    @Resource(name = "aiToolExecutor")
    private Executor aiToolExecutor;

    /** 聊天 SSE 超时（毫秒）：必须与上游 HTTP 请求超时（5min）对齐，否则慢模型未返回时 emitter 先被 Spring complete，
     * 后续 send() 抛 "already completed" 被 isClientDisconnect 误判为客户端断开而静默 return，导致漏计费、漏落历史。 */
    private static final long CHAT_SSE_TIMEOUT = Duration.ofMinutes(5).toMillis();

    /** 当前请求主体：登录按账号，游客按 IP（同步阶段解析，避免异步线程无 web 上下文）。 */
    private record Subject(String type, String id) {}

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
            String endpoint = SafeUrlValidator.normalizePublicBaseUrl(request.getEndpoint(), "自定义 AI endpoint");
            return new AiResolvedConfig(null, null,
                    endpoint, request.getApiKey(), request.getModel(), "TEXT");
        }
        if (request.getModelId() != null) {
            return aiModelService.resolveConfig(request.getModelId());
        }
        return aiModelService.resolveDefaultConfig("TEXT");
    }

    private SseEmitter doStreamChat(AiResolvedConfig cfg, String prompt) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();
        Subject subject = currentSubject();

        SseEmitter emitter = new SseEmitter(CHAT_SSE_TIMEOUT);
        registerEmitterLifecycle(emitter, subject.type(), subject.id());

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", true);
            // 请求 LLM 在流式末帧返回 usage（OpenAI 兼容）
            body.put("stream_options", Map.of("include_usage", true));
            HttpRequest httpRequest = llmClient.buildRequest(cfg, body, Duration.ofMinutes(5));

            log.info("AI 流式请求: model={}, type={}, providerId={}, promptLen={}, subject={}:{}",
                    model, cfg.modelType(), cfg.providerId(), prompt.length(), subject.type(), subject.id());

            final String finalSubjectType = subject.type();
            final String finalSubjectId = subject.id();
            // 历史落库用：请求时刻、供应商/模型（lambda 内 final 捕获）
            final LocalDateTime requestTime = LocalDateTime.now();
            final long requestTimeMillis = System.currentTimeMillis();
            final Long providerId = cfg.providerId();
            final String finalModel = model;
            final boolean[] recorded = {false};
            final String operator = finalSubjectType + ":" + finalSubjectId;
            final String callParams = "model=" + finalModel + ", providerId=" + providerId
                    + ", promptLen=" + prompt.length() + ", subject=" + operator;

            llmClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            String errMsg = "LLM 返回错误: HTTP " + response.statusCode();
                            aiUsageRecorder.recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, null, "failed", errMsg, requestTime, requestTimeMillis);
                            externalCallLogger.failure("LLM流式", url, callParams, errMsg,
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sendError(emitter, errMsg);
                            return;
                        }
                        SseEmitterChannel sender = new SseEmitterChannel(emitter);
                        try (InputStream is = response.body()) {
                            LlmChatClient.ChatStreamReply sr = llmClient.parseStream(is,
                                    delta -> sender.send(Map.of("content", delta)));
                            aiUsageRecorder.recordTokenUsage(cfg, finalModel, prompt, sr.content(),
                                    sr.hasUsage(), sr.usage(), finalSubjectType, finalSubjectId);
                            aiUsageRecorder.recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, sr.content(), "success", null, requestTime, requestTimeMillis);
                            externalCallLogger.success("LLM流式", url, callParams + ", resultLen=" + sr.content().length(),
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sender.sendAndComplete(Map.of("done", true));
                            log.info("AI 流式响应完成 tokens={}/{}/{} source={}",
                                    sr.usage()[0], sr.usage()[1], sr.usage()[2],
                                    sr.hasUsage() ? "usage" : "estimate");
                        } catch (Exception e) {
                            if (isClientDisconnect(e)) {
                                log.warn("AI 流式客户端断开 subject={}:{} msg={}", finalSubjectType, finalSubjectId, e.getMessage());
                                return;
                            }
                            log.error("AI 流式响应中断 model={} subject={}:{}: {}",
                                    finalModel, finalSubjectType, finalSubjectId, e.getMessage(), e);
                            aiUsageRecorder.recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                    prompt, null, "failed", e.getMessage(), requestTime, requestTimeMillis);
                            externalCallLogger.failure("LLM流式", url, callParams, e.getMessage(),
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sendError(emitter, e.getMessage());
                        } finally {
                            sender.shutdown();
                        }
                    })
                    .exceptionally(e -> {
                        log.error("调用 LLM 失败: {}", e.getMessage(), e);
                        aiUsageRecorder.recordHistory(recorded, finalSubjectType, finalSubjectId, providerId, finalModel,
                                prompt, null, "failed", e.getMessage(), requestTime, requestTimeMillis);
                        externalCallLogger.failure("LLM流式", url, callParams, e.getMessage(),
                                System.currentTimeMillis() - requestTimeMillis, operator);
                        sendError(emitter, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            throw new BusinessException("构建 AI 请求失败: " + e.getMessage());
        }

        return emitter;
    }

    private String doChatCompletion(AiResolvedConfig cfg, String prompt) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();
        Subject subject = currentSubject();
        long start = System.currentTimeMillis();
        String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                + ", promptLen=" + prompt.length() + ", subject=" + subject.type() + ":" + subject.id();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", false);
            // 海螺只需短 JSON（top-3 id），限制输出加速生成；低温更确定
            body.put("max_tokens", 100);
            body.put("temperature", 0.3);
            HttpRequest httpRequest = llmClient.buildRequest(cfg, body, Duration.ofMinutes(2));

            log.info("AI 非流式请求: model={}, type={}, providerId={}, promptLen={}, subject={}:{}",
                    model, cfg.modelType(), cfg.providerId(), prompt.length(), subject.type(), subject.id());

            HttpResponse<String> response = llmClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("LLM 返回错误: HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0)
                    .path("message").path("content").asText("");
            int[] usage = llmClient.parseUsage(json.path("usage"));
            aiUsageRecorder.recordTokenUsage(cfg, model, prompt, content, usage != null, usage,
                    subject.type(), subject.id());

            externalCallLogger.success("LLM非流式", url, callParams + ", resultLen=" + content.length(),
                    System.currentTimeMillis() - start);
            log.info("AI 非流式响应完成 tokens={}/{}/{} source={}",
                    usage == null ? 0 : usage[0], usage == null ? 0 : usage[1],
                    usage == null ? 0 : usage[2], usage != null ? "usage" : "estimate");
            return content;
        } catch (BusinessException e) {
            externalCallLogger.failure("LLM非流式", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("LLM非流式", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI 非流式调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            // 前端 fetch+ReadableStream 只解析默认 message 事件的 data；
            // 历史上这里用了 name("error") 命名事件，导致错误被静默丢弃、页面只看到空白块
            emitter.send(SseEmitter.event()
                    .data(Map.of("error", message == null ? "AI 服务异常" : message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            // 降级：连接已断无法推送错误，completeWithError 触发清理
            log.warn("SSE 错误事件发送失败（连接已断）: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    // ===== 多轮聊天（AI 聊天模块用）=====
    // 多轮流式不落 ai_task；消息历史由 AiChatSessionService 落 ai_chat_message。
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
    /**
     * 创建聊天 SSE emitter（供 {@link AiChatSessionService} 在 RAG 检索前先建好连接，
     * 检索进度可经 {@link #sendStatus} 推给前端）。必须在主线程调用（内部解析登录态/客户端 IP）。
     */
    public SseEmitter createChatEmitter() {
        return createChatEmitter(currentSubject());
    }

    private SseEmitter createChatEmitter(Subject subject) {
        SseEmitter emitter = new SseEmitter(CHAT_SSE_TIMEOUT);
        registerEmitterLifecycle(emitter, subject.type(), subject.id());
        return emitter;
    }

    /** 向 SSE 发 status 事件（检索进度等），发送失败忽略（连接已断/未建立等）。 */
    public void sendStatus(SseEmitter emitter, String status) {
        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event().data(Map.of("status", status), MediaType.APPLICATION_JSON));
            }
        } catch (Exception e) {
            // 降级：状态进度推送失败不影响主流程，多为连接已断
            log.warn("SSE 状态事件发送失败 status={}: {}", status, e.getMessage(), e);
        }
    }

    public SseEmitter streamMultiChat(List<Map<String, String>> messages, Long modelId,
                                      Consumer<String> onComplete) {
        Subject subject = currentSubject();
        SseEmitter emitter = createChatEmitter(subject);
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doStreamChatMulti(emitter, messages, cfg, onComplete, subject);
    }

    /**
     * 异步线程版（主体已在主线程解析传入，避免异步线程无 sa-token/request 上下文）。
     * emitter 由调用方先创建（{@link #createChatEmitter}）。
     */
    public SseEmitter streamMultiChat(SseEmitter emitter, List<Map<String, String>> messages, Long modelId,
                                      Consumer<String> onComplete, String subjectType, String subjectId) {
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doStreamChatMulti(emitter, messages, cfg, onComplete, new Subject(subjectType, subjectId));
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

    private SseEmitter doStreamChatMulti(SseEmitter emitter, List<Map<String, String>> messages,
                                         AiResolvedConfig cfg, Consumer<String> onComplete, Subject subject) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            HttpRequest httpRequest = llmClient.buildRequest(cfg, body, Duration.ofMinutes(5));

            log.info("AI 多轮流式: model={}, msgCount={}, subject={}:{}", model, messages.size(), subject.type(), subject.id());

            long requestTimeMillis = System.currentTimeMillis();
            String operator = subject.type() + ":" + subject.id();
            String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                    + ", msgCount=" + messages.size() + ", subject=" + operator;

            llmClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            String errMsg = "LLM 返回错误: HTTP " + response.statusCode();
                            externalCallLogger.failure("LLM多轮流式", url, callParams, errMsg,
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sendError(emitter, errMsg);
                            return;
                        }
                        SseEmitterChannel sender = new SseEmitterChannel(emitter);
                        try (InputStream is = response.body()) {
                            LlmChatClient.ChatStreamReply sr = llmClient.parseStream(is,
                                    delta -> sender.send(Map.of("content", delta)));
                            aiUsageRecorder.recordTokenUsageForMessages(cfg, model, messages, sr.content(),
                                    sr.hasUsage(), sr.usage(), subject.type(), subject.id());
                            if (onComplete != null) {
                                try {
                                    onComplete.accept(sr.content());
                                } catch (Exception ce) {
                                    // 降级：回调仅负责落消息表等收尾，失败不影响流式输出，但必须可查
                                    log.warn("多轮流式 onComplete 回调失败（不影响流式）operator={}: {}",
                                            operator, ce.getMessage(), ce);
                                }
                            }
                            externalCallLogger.success("LLM多轮流式", url, callParams + ", resultLen=" + sr.content().length(),
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sender.sendAndComplete(Map.of("done", true));
                            log.info("AI 多轮流式完成 tokens={}/{}/{}", sr.usage()[0], sr.usage()[1], sr.usage()[2]);
                        } catch (Exception e) {
                            if (isClientDisconnect(e)) {
                                log.warn("AI 多轮流式客户端断开 subject={} msg={}", operator, e.getMessage());
                                return;
                            }
                            log.error("AI 多轮流式响应中断 model={} subject={}: {}", model, operator, e.getMessage(), e);
                            externalCallLogger.failure("LLM多轮流式", url, callParams, e.getMessage(),
                                    System.currentTimeMillis() - requestTimeMillis, operator);
                            sendError(emitter, e.getMessage());
                        } finally {
                            sender.shutdown();
                        }
                    })
                    .exceptionally(e -> {
                        log.error("调用 LLM 失败: {}", e.getMessage(), e);
                        externalCallLogger.failure("LLM多轮流式", url, callParams, e.getMessage(),
                                System.currentTimeMillis() - requestTimeMillis, operator);
                        sendError(emitter, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            throw new BusinessException("构建 AI 请求失败: " + e.getMessage());
        }
        return emitter;
    }

    private String doChatCompletionMessages(List<Map<String, String>> messages, AiResolvedConfig cfg) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();
        Subject subject = currentSubject();
        long start = System.currentTimeMillis();
        String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                + ", msgCount=" + messages.size() + ", subject=" + subject.type() + ":" + subject.id();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            HttpRequest httpRequest = llmClient.buildRequest(cfg, body, Duration.ofMinutes(5));

            log.info("AI 非流式多消息: model={}, msgCount={}, subject={}:{}", model, messages.size(), subject.type(), subject.id());
            HttpResponse<String> response = llmClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("LLM 返回错误: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            int[] usage = llmClient.parseUsage(json.path("usage"));
            aiUsageRecorder.recordTokenUsageForMessages(cfg, model, messages, content, usage != null, usage,
                    subject.type(), subject.id());
            externalCallLogger.success("LLM非流式多消息", url, callParams + ", resultLen=" + content.length(),
                    System.currentTimeMillis() - start);
            log.info("AI 非流式多消息完成 tokens={}/{}/{}",
                    usage == null ? 0 : usage[0], usage == null ? 0 : usage[1], usage == null ? 0 : usage[2]);
            return content;
        } catch (BusinessException e) {
            externalCallLogger.failure("LLM非流式多消息", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("LLM非流式多消息", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI 非流式多消息调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + e.getMessage());
        }
    }

    // ===== 聊天工具调用（function calling）=====

    /**
     * 非流式多消息 + tools（function calling 判定用）。
     * <p>请求体加 tools + tool_choice=auto，返回完整响应 JsonNode，由调用方解析 choices[0].message.tool_calls。
     * 不限制 max_tokens（让 LLM 自由决定是否调工具）。
     */
    public JsonNode chatCompletionWithTools(List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools, Long modelId) {
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doChatCompletionWithTools(messages, tools, cfg);
    }

    private JsonNode doChatCompletionWithTools(List<Map<String, Object>> messages,
                                               List<Map<String, Object>> tools, AiResolvedConfig cfg) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();
        Subject subject = currentSubject();
        long start = System.currentTimeMillis();
        String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                + ", msgCount=" + messages.size() + ", tools=" + (tools != null ? tools.size() : 0)
                + ", subject=" + subject.type() + ":" + subject.id();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
                body.put("tool_choice", "auto");
            }
            HttpRequest httpRequest = llmClient.buildRequest(cfg, body, Duration.ofMinutes(2));

            log.info("AI 工具判定(非流式): model={}, msgCount={}, tools={}, subject={}:{}",
                    model, messages.size(), tools != null ? tools.size() : 0, subject.type(), subject.id());

            HttpResponse<String> response = llmClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                // 上游错误体可能含用户内容，截断后记录，避免日志泄露
                log.error("AI 工具判定上游错误: HTTP {} body={}", response.statusCode(), truncate(response.body(), 200));
                throw new BusinessException("LLM 返回错误: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode msgNode = json.path("choices").path(0).path("message");
            int[] usage = llmClient.parseUsage(json.path("usage"));
            aiUsageRecorder.recordTokenUsageForMessages(cfg, model, messages, msgNode.path("content").asText(""),
                    usage != null, usage, subject.type(), subject.id());
            externalCallLogger.success("LLM工具判定(非流式)", url, callParams
                            + ", hasToolCalls=" + msgNode.path("tool_calls").isArray(),
                    System.currentTimeMillis() - start);
            return json;
        } catch (BusinessException e) {
            externalCallLogger.failure("LLM工具判定(非流式)", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("LLM工具判定(非流式)", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI 工具判定调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + e.getMessage());
        }
    }

    /**
     * 多轮流式聊天 + 工具调用（登录用户聊天入口）。
     * <p>编排：第一次非流式带 tools 判定 LLM 是否调工具 →
     * 无 tool_calls：直接把 content 一次性流式转发 + done；
     * 有 tool_calls：执行工具 → messages 追加 [assistant(tool_call), tool(result)] →
     * 发进度事件(generating_image/generating_audio) → 第二次流式(不带 tools 避免无限循环) →
     * 转发流式分片 + done → onComplete(AiChatResult(最终文本, 附件))。
     * <p>仅暴露 tools 给登录用户（调用方保证），游客走 {@link #streamMultiChat} 纯文本。
     * <p>SseEmitter 超时 5 分钟（图片生成可能 30s + 流式 60s）。
     */
    public SseEmitter streamMultiChatWithTools(List<Map<String, Object>> messages, Long modelId,
                                               List<Map<String, Object>> tools, String voice,
                                               Consumer<AiChatResult> onComplete) {
        Subject subject = currentSubject();
        SseEmitter emitter = createChatEmitter(subject);
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doStreamChatWithTools(emitter, messages, cfg, tools, voice, onComplete, subject);
    }

    /**
     * 异步线程版（主体已在主线程解析传入，避免异步线程无 sa-token/request 上下文）。
     * emitter 由调用方先创建（{@link #createChatEmitter}）。
     */
    public SseEmitter streamMultiChatWithTools(SseEmitter emitter, List<Map<String, Object>> messages, Long modelId,
                                               List<Map<String, Object>> tools, String voice,
                                               Consumer<AiChatResult> onComplete,
                                               String subjectType, String subjectId) {
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("TEXT");
        return doStreamChatWithTools(emitter, messages, cfg, tools, voice, onComplete,
                new Subject(subjectType, subjectId));
    }

    private SseEmitter doStreamChatWithTools(SseEmitter emitter, List<Map<String, Object>> messages,
                                             AiResolvedConfig cfg, List<Map<String, Object>> tools,
                                             String voice, Consumer<AiChatResult> onComplete, Subject subject) {
        String url = llmClient.chatCompletionsUrl(cfg);
        String model = cfg.model();

        try {
            // 第一次非流式（带 tools）
            Map<String, Object> firstBody = new HashMap<>();
            firstBody.put("model", model);
            firstBody.put("messages", messages);
            firstBody.put("stream", false);
            if (tools != null && !tools.isEmpty()) {
                firstBody.put("tools", tools);
                firstBody.put("tool_choice", "auto");
            }
            HttpRequest firstRequest = llmClient.buildRequest(cfg, firstBody, Duration.ofMinutes(2));

            log.info("AI 聊天工具判定: model={}, msgCount={}, tools={}, subject={}:{}",
                    model, messages.size(), tools != null ? tools.size() : 0, subject.type(), subject.id());
            if (tools != null && !tools.isEmpty()) {
                log.info("AI 聊天工具判定请求体含 tools，tool_choice=auto");
            } else {
                log.warn("AI 聊天工具判定请求体不含 tools（为空），LLM 不知道有工具可用");
            }

            long firstStart = System.currentTimeMillis();
            String operator = subject.type() + ":" + subject.id();
            String firstCallParams = "model=" + model + ", providerId=" + cfg.providerId()
                    + ", msgCount=" + messages.size() + ", tools=" + (tools != null ? tools.size() : 0)
                    + ", subject=" + operator;

            llmClient.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenAccept(firstResponse -> {
                        if (firstResponse.statusCode() != 200) {
                            // 上游错误体可能含用户内容，截断后记录，避免日志泄露
                            log.error("AI 聊天工具判定失败: HTTP {} body={}",
                                    firstResponse.statusCode(), truncate(firstResponse.body(), 200));
                            externalCallLogger.failure("LLM工具判定", url, firstCallParams,
                                    "HTTP " + firstResponse.statusCode(),
                                    System.currentTimeMillis() - firstStart, operator);
                            sendError(emitter, "LLM 返回错误: HTTP " + firstResponse.statusCode());
                            safeOnComplete(onComplete, new AiChatResult(null, null, null));
                            return;
                        }
                        try {
                            JsonNode firstJson = objectMapper.readTree(firstResponse.body());
                            JsonNode firstMsg = firstJson.path("choices").path(0).path("message");
                            JsonNode toolCallsNode = firstMsg.path("tool_calls");

                            // 仅记录结构信息（是否触发工具、数量、内容长度），不落响应体原文，避免泄露用户内容
                            boolean hasToolCalls = toolCallsNode.isArray() && !toolCallsNode.isEmpty();
                            log.info("AI 聊天工具判定响应: hasToolCalls={}, toolCallsCount={}, contentLen={}",
                                    hasToolCalls,
                                    toolCallsNode.isArray() ? toolCallsNode.size() : 0,
                                    firstMsg.path("content").asText("").length());
                            externalCallLogger.success("LLM工具判定", url, firstCallParams
                                            + ", hasToolCalls=" + hasToolCalls
                                            + ", toolCallsCount=" + (toolCallsNode.isArray() ? toolCallsNode.size() : 0),
                                    System.currentTimeMillis() - firstStart, operator);

                            // 记第一次 token
                            int[] usage1 = llmClient.parseUsage(firstJson.path("usage"));
                            aiUsageRecorder.recordTokenUsageForMessages(cfg, model, messages, firstMsg.path("content").asText(""),
                                    usage1 != null, usage1, subject.type(), subject.id());

                            if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
                                // 无 tool_calls：直接把 content 一次性流式转发
                                String content = firstMsg.path("content").asText("");
                                if (!content.isEmpty()) {
                                    emitter.send(SseEmitter.event().data(Map.of("content", content), MediaType.APPLICATION_JSON));
                                }
                                emitter.send(SseEmitter.event().data(Map.of("done", true), MediaType.APPLICATION_JSON));
                                emitter.complete();
                                safeOnComplete(onComplete, new AiChatResult(content, null, null));
                                log.info("AI 聊天工具判定: 无工具调用，直接返回文本 len={}", content.length());
                                return;
                            }

                            // 有 tool_calls：取第一个（提示词已约束单工具，违规只执行首个）
                            JsonNode toolCall = toolCallsNode.get(0);
                            if (toolCallsNode.size() > 1) {
                                log.warn("AI 聊天工具判定: LLM 返回 {} 个 tool_calls，仅执行第一个", toolCallsNode.size());
                            }
                            aiToolExecutor.execute(() -> executeToolAndContinue(toolCall, voice, subject, messages,
                                    emitter, url, cfg, model, onComplete, firstCallParams, firstStart, operator));
                        } catch (Exception e) {
                            log.error("AI 聊天工具判定处理失败: {}", e.getMessage(), e);
                            externalCallLogger.failure("LLM工具判定", url, firstCallParams, e.getMessage(),
                                    System.currentTimeMillis() - firstStart, operator);
                            sendError(emitter, e.getMessage());
                            safeOnComplete(onComplete, new AiChatResult(null, null, null));
                        }
                    })
                    .exceptionally(e -> {
                        log.error("AI 聊天工具判定调用失败: {}", e.getMessage(), e);
                        externalCallLogger.failure("LLM工具判定", url, firstCallParams, e.getMessage(),
                                System.currentTimeMillis() - firstStart, operator);
                        sendError(emitter, e.getMessage());
                        safeOnComplete(onComplete, new AiChatResult(null, null, null));
                        return null;
                    });
        } catch (Exception e) {
            log.error("构建 AI 聊天工具请求失败: {}", e.getMessage(), e);
            throw new BusinessException("构建 AI 请求失败: " + e.getMessage());
        }
        return emitter;
    }

    /** 第二次流式调用（工具执行后），不带 tools，转发流式分片 + done + onComplete(最终文本,附件) */
    private void doSecondStream(SseEmitter emitter, String url, AiResolvedConfig cfg, String model,
                                List<Map<String, Object>> messages, Consumer<AiChatResult> onComplete,
                                ToolResult toolResult, String subjectType, String subjectId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            HttpRequest request = llmClient.buildRequest(cfg, body, Duration.ofMinutes(5));

            log.info("AI 聊天第二次流式(工具后): model={}, msgCount={}", model, messages.size());

            long start = System.currentTimeMillis();
            String operator = subjectType + ":" + subjectId;
            String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                    + ", msgCount=" + messages.size() + ", subject=" + operator;

            llmClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            log.error("AI 聊天第二次流式失败: HTTP {}", response.statusCode());
                            externalCallLogger.failure("LLM工具后流式", url, callParams,
                                    "HTTP " + response.statusCode(), System.currentTimeMillis() - start, operator);
                            sendError(emitter, "LLM 返回错误: HTTP " + response.statusCode());
                            safeOnComplete(onComplete, new AiChatResult(null, toolResult.attachmentType(), toolResult.attachmentFileId()));
                            return;
                        }
                        SseEmitterChannel sender = new SseEmitterChannel(emitter);
                        try (InputStream is = response.body()) {
                            LlmChatClient.ChatStreamReply sr = llmClient.parseStream(is,
                                    delta -> sender.send(Map.of("content", delta)));
                            aiUsageRecorder.recordTokenUsageForMessages(cfg, model, messages, sr.content(),
                                    sr.hasUsage(), sr.usage(), subjectType, subjectId);
                            externalCallLogger.success("LLM工具后流式", url, callParams + ", resultLen=" + sr.content().length(),
                                    System.currentTimeMillis() - start, operator);
                            sender.sendAndComplete(Map.of("done", true));
                            safeOnComplete(onComplete, new AiChatResult(sr.content(),
                                    toolResult.attachmentType(), toolResult.attachmentFileId()));
                            log.info("AI 聊天第二次流式完成 tokens={}/{}/{}", sr.usage()[0], sr.usage()[1], sr.usage()[2]);
                        } catch (Exception e) {
                            if (isClientDisconnect(e)) {
                                log.warn("AI 工具后流式客户端断开 subject={} msg={}", operator, e.getMessage());
                                return;
                            }
                            log.error("AI 工具后流式响应中断 model={} subject={}: {}", model, operator, e.getMessage(), e);
                            externalCallLogger.failure("LLM工具后流式", url, callParams, e.getMessage(),
                                    System.currentTimeMillis() - start, operator);
                            sendError(emitter, e.getMessage());
                        } finally {
                            sender.shutdown();
                        }
                    })
                    .exceptionally(e -> {
                        log.error("AI 聊天第二次流式调用失败: {}", e.getMessage(), e);
                        externalCallLogger.failure("LLM工具后流式", url, callParams, e.getMessage(),
                                System.currentTimeMillis() - start, operator);
                        sendError(emitter, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("构建第二次流式请求失败: {}", e.getMessage(), e);
            sendError(emitter, e.getMessage());
        }
    }

    // ===== 私有 helper（去重核心）=====

    /** 解析当前请求主体：登录按账号，游客按 IP（同步阶段调用，保证 web 上下文可用）。 */
    private Subject currentSubject() {
        if (StpUtil.isLogin()) {
            return new Subject("account", StpUtil.getLoginIdAsString());
        }
        return new Subject("ip", WebUtils.getClientIp());
    }

/**
      * 注册 SSE 生命周期回调：超时/完成/错误时记日志，便于排查连接泄漏。
      * <p>不手动调 emitter.complete()——Spring 在 onTimeout/onError 回调后会自动 complete。
      */
    private void registerEmitterLifecycle(SseEmitter emitter, String subjectType, String subjectId) {
        emitter.onTimeout(() -> log.warn("SSE 超时断开 subject={}:{}", subjectType, subjectId));
        emitter.onCompletion(() -> log.debug("SSE 连接关闭 subject={}:{}", subjectType, subjectId));
        emitter.onError((e) -> log.warn("SSE 传输错误 subject={}:{} msg={}", subjectType, subjectId, e.getMessage()));
    }

    /** 截断字符串用于日志（避免长响应体/用户内容进入日志）。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 安全回调 onComplete，异常吞掉不影响流式主流程 */
    private void safeOnComplete(Consumer<AiChatResult> onComplete, AiChatResult result) {
        if (onComplete == null) {
            return;
        }
        try {
            onComplete.accept(result);
        } catch (Exception e) {
            // 降级：回调仅负责落消息等收尾，失败不影响流式输出，但需可查
            log.warn("onComplete 回调失败（不影响流式）: {}", e.getMessage(), e);
        }
    }

    private void executeToolAndContinue(JsonNode toolCall, String voice, Subject subject,
                                        List<Map<String, Object>> messages, SseEmitter emitter,
                                        String url, AiResolvedConfig cfg, String model,
                                        Consumer<AiChatResult> onComplete, String callParams,
                                        long start, String operator) {
        try {
            ToolResult toolResult = aiChatToolService.execute(toolCall, voice, subject.type(), subject.id());
            log.info("AI 聊天工具执行: name={}, toolCallId={}, attachmentType={}, attachmentFileId={}",
                    toolCall.path("function").path("name").asText(""), toolResult.toolCallId(),
                    toolResult.attachmentType(), toolResult.attachmentFileId());
            List<Map<String, Object>> secondMessages = new ArrayList<>(messages);
            Map<String, Object> assistantToolMsg = new HashMap<>();
            assistantToolMsg.put("role", "assistant");
            assistantToolMsg.put("content", null);
            assistantToolMsg.put("tool_calls", objectMapper.convertValue(toolCall, Map.class));
            secondMessages.add(assistantToolMsg);
            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", toolResult.toolCallId());
            toolMsg.put("content", toolResult.content());
            secondMessages.add(toolMsg);
            String toolName = toolCall.path("function").path("name").asText("");
            String status = "generate_image".equals(toolName) ? "generating_image" : "generating_audio";
            emitter.send(SseEmitter.event().data(Map.of("status", status), MediaType.APPLICATION_JSON));
            doSecondStream(emitter, url, cfg, model, secondMessages, onComplete, toolResult,
                    subject.type(), subject.id());
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                log.warn("AI 工具执行后客户端断开 subject={} msg={}", operator, e.getMessage());
                return;
            }
            log.error("AI 聊天工具执行链失败 model={} subject={}: {}", model, operator, e.getMessage(), e);
            externalCallLogger.failure("LLM工具执行", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start, operator);
            sendError(emitter, e.getMessage());
            safeOnComplete(onComplete, new AiChatResult(null, null, null));
        }
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SseEmitterChannel.ClientDisconnectedException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("broken pipe") || lower.contains("connection reset")
                        || lower.contains("async request not usable")
                        || lower.contains("responsebodyemitter has already completed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

}
