package com.stellar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.entity.SysAiConfig;
import com.stellar.mapper.SysAiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
 * AI 流式聊天服务，通过 SseEmitter 将 LLM 的 SSE 流转发给前端。
 * <p>
 * 后端代理调用 LLM 端点，API Key 不暴露给浏览器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 流式聊天，返回 SseEmitter。
     * <p>
     * 每个 LLM delta 以 {"content":"xxx"} 事件转发，结束时发 {"done":true}。
     */
    public SseEmitter streamChat(String prompt) {
        SysAiConfig config = aiConfigService.getRawConfig();
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new BusinessException("AI 接口未配置");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new BusinessException("AI API Key 未配置");
        }
        if (!StringUtils.hasText(config.getModel())) {
            throw new BusinessException("AI 模型未配置");
        }

        String endpoint = config.getEndpoint().replaceAll("/+$", "");
        String url = endpoint + "/v1/chat/completions";

        SseEmitter emitter = new SseEmitter(120000L);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", true);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 流式请求: model={}, promptLen={}", config.getModel(), prompt.length());

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            sendError(emitter, "LLM 返回错误: HTTP " + response.statusCode());
                            return;
                        }
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
                                        emitter.send(SseEmitter.event()
                                                .data(Map.of("content", delta),
                                                        MediaType.APPLICATION_JSON));
                                    }
                                } catch (Exception e) {
                                    log.debug("解析 LLM 响应分片失败: {}", data);
                                }
                            }
                            emitter.send(SseEmitter.event()
                                    .data(Map.of("done", true), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            log.info("AI 流式响应完成");
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
