package com.stellar.ai.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.infra.SafeUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@link LlmChatClient} 的 OpenAI 兼容实现：/v1/chat/completions + Bearer 鉴权 +
 * SSE data 分帧（choices[0].delta.content / usage 三元组）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiHttpChatClient implements LlmChatClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String chatCompletionsUrl(AiResolvedConfig cfg) {
        String base = cfg.providerId() == null
                ? SafeUrlValidator.normalizePublicBaseUrl(cfg.endpoint(), "自定义 AI endpoint")
                : cfg.endpoint().replaceAll("/+$", "");
        return base + "/v1/chat/completions";
    }

    @Override
    public HttpRequest buildRequest(AiResolvedConfig cfg, Map<String, Object> body, Duration timeout)
            throws JsonProcessingException {
        String bodyJson = objectMapper.writeValueAsString(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(cfg)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        return httpClient.sendAsync(request, handler);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return httpClient.send(request, handler);
    }

    @Override
    public ChatStreamReply parseStream(InputStream is, ChunkSink sink) throws IOException {
        StringBuilder buf = new StringBuilder();
        int[] usage = {0, 0, 0};
        boolean[] hasUsage = {false};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) {
                    break;
                }
                try {
                    JsonNode json = objectMapper.readTree(data);
                    String delta = json.path("choices").path(0)
                            .path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        buf.append(delta);
                        sink.accept(delta);
                    }
                    int[] frameUsage = parseUsage(json.path("usage"));
                    if (frameUsage != null) {
                        usage = frameUsage;
                        hasUsage[0] = true;
                    }
                } catch (JsonProcessingException e) {
                    // 单个分片 JSON 非法：跳过该分片继续读（与原实现一致）
                    log.debug("解析 LLM 响应分片失败: {}", data);
                } catch (IOException e) {
                    throw e;   // 客户端断开等 IO 失败（含 ChunkSink 抛出的断开信号）上抛，中断上游读取
                }
            }
        }
        return new ChatStreamReply(buf.toString(), usage, hasUsage[0]);
    }

    @Override
    public int[] parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || !usageNode.has("total_tokens")) {
            return null;
        }
        return new int[]{
                usageNode.path("prompt_tokens").asInt(0),
                usageNode.path("completion_tokens").asInt(0),
                usageNode.path("total_tokens").asInt(0)
        };
    }
}