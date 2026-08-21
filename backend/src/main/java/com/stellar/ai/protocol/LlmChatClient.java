package com.stellar.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.stellar.ai.vo.AiResolvedConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 供应商协议缝：聊天补全的传输层抽象。
 * <p>协议相关关注点全部收敛于此——端点 URL 形态、请求头/鉴权方式、SSE 分帧格式、usage 字段名。
 * 新增 Anthropic / Gemini 等原生协议时新增实现即可，上层编排（AiChatService）不感知协议差异；
 * 供应商 endpoint/apiKey 已是 DB 驱动，本接口解决的是"协议"而非"配置"。
 * <p>发送保留同步/异步两个原语，调用方自行编排回调时序（与既有 sendAsync 编排保持一致）。
 */
public interface LlmChatClient {

    /** 组装协议端点 URL（如 OpenAI 兼容 base + /v1/chat/completions）。 */
    String chatCompletionsUrl(AiResolvedConfig cfg);

    /** 构建 POST 请求体 JSON 并组装 HTTP 请求（Content-Type + Bearer 鉴权）。 */
    HttpRequest buildRequest(AiResolvedConfig cfg, Map<String, Object> body, Duration timeout) throws IOException;

    /** 异步发送（流式场景配 ofInputStream，非流式配 ofString）。 */
    <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler);

    /** 同步发送。 */
    <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException;

    /**
     * 解析 SSE 流式响应：逐行读取 data 分片，每条内容分片回调 {@link ChunkSink}，
     * 提取末帧 usage；收到 [DONE] 或流结束返回累积结果。
     * <p>sink 发送失败（客户端断开）抛出的 IOException 原样向上传播，中断上游读取。
     */
    ChatStreamReply parseStream(InputStream is, ChunkSink sink) throws IOException;

    /** 从 usage 节点提取 [prompt, completion, total]；无 total_tokens 视为未返回，给 null。 */
    int[] parseUsage(JsonNode usageNode);

    /** 流式内容分片出口：实现方发送失败抛 IOException 即可中断解析循环。 */
    @FunctionalInterface
    interface ChunkSink {

        void accept(String delta) throws IOException;
    }

    /** 流式解析结果：累积的完整文本 + 用量 + 是否来自 LLM usage。 */
    record ChatStreamReply(String content, int[] usage, boolean hasUsage) {
    }
}