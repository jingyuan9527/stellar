package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.vo.AiResolvedConfig;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 语音合成服务：基于 OpenAI 兼容的 chat completions + audio 参数。
 * <p>当前对接 MiMo-V2.5-TTS 预置音色模式：合成文本放 role=assistant 消息，
 * 风格指令放 role=user 消息（可空），audio.voice 指定预置音色，audio.format=wav。
 * 非流式调用，响应 message.audio.data 为 base64 wav，解码后返回。
 * <p>配置按 modelId 解析 AUDIO 类型模型（system/ai-config 配置供应商+模型），记 token 消费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTtsService {

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;
    private final SysAiUsageService sysAiUsageService;
    private final ExternalCallLogger externalCallLogger;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * AI 语音合成，返回 WAV 字节数组。
     *
     * @param modelId AUDIO 类型模型 ID
     * @param text    合成文本（放 assistant 消息）
     * @param voice   预置音色，如 冰糖/Chloe
     * @param style   风格指令（自然语言，放 user 消息；可空）
     * @return WAV 音频字节
     */
    public byte[] synthesize(Long modelId, String text, String voice, String style) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("合成文本不能为空");
        }
        if (!StringUtils.hasText(voice)) {
            throw new BusinessException("音色不能为空");
        }

        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"AUDIO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是语音合成类型，请选择 AUDIO 类型模型");
        }

        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/chat/completions";
        String model = cfg.model();

        // messages：assistant 放合成文本，user 放风格指令（空则给空串，保持消息结构）
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", style != null ? style : "");
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", text);

        // 主体判断（同步阶段，request 上下文可用）；AI TTS 仅登录可用，主体恒为 account
        String subjectType;
        String subjectId;
        if (StpUtil.isLogin()) {
            subjectType = "account";
            subjectId = StpUtil.getLoginIdAsString();
        } else {
            subjectType = "ip";
            subjectId = getClientIp();
        }

        long start = System.currentTimeMillis();
        String callParams = "model=" + model + ", providerId=" + cfg.providerId()
                + ", voice=" + voice + ", textLen=" + text.length()
                + ", styleLen=" + (style == null ? 0 : style.length())
                + ", subject=" + subjectType + ":" + subjectId;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(userMsg, assistantMsg));
            body.put("stream", false);
            body.put("audio", Map.of("format", "wav", "voice", voice));
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI TTS 请求: model={}, voice={}, providerId={}, textLen={}, styleLen={}, subject={}:{}",
                    model, voice, cfg.providerId(), text.length(),
                    style == null ? 0 : style.length(), subjectType, subjectId);

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("AI TTS 上游返回错误: HTTP {} body={}", response.statusCode(), response.body());
                throw new BusinessException("语音合成服务返回错误: HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            // 音频数据在 choices[0].message.audio.data（base64 编码的 WAV）
            JsonNode audioNode = json.path("choices").path(0)
                    .path("message").path("audio");
            String audioB64 = audioNode.path("data").asText("");
            if (!StringUtils.hasText(audioB64)) {
                log.error("AI TTS 响应未包含音频数据: body={}", response.body());
                throw new BusinessException("语音合成失败：未收到音频数据");
            }
            byte[] audio = Base64.getDecoder().decode(audioB64);

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
                promptTokens = estimateTokens(text) + (style == null ? 0 : estimateTokens(style));
                completionTokens = 0;
                totalTokens = promptTokens;
                source = "estimate";
            }
            sysAiUsageService.record(subjectType, subjectId,
                    cfg.providerId(), model, cfg.modelType(),
                    promptTokens, completionTokens, totalTokens, source);

            externalCallLogger.success("AI TTS", url, callParams + ", resultBytes=" + audio.length
                    + ", tokens=" + promptTokens + "/" + completionTokens + "/" + totalTokens + "/" + source,
                    System.currentTimeMillis() - start);
            log.info("AI TTS 响应完成: {} bytes, tokens={}/{}/{} source={}",
                    audio.length, promptTokens, completionTokens, totalTokens, source);
            return audio;
        } catch (BusinessException e) {
            externalCallLogger.failure("AI TTS", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("AI TTS", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("AI TTS 调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 语音合成失败: " + e.getMessage());
        }
    }

    private int estimateTokens(String text) {
        return text == null || text.isEmpty() ? 0 : text.length();
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
}
