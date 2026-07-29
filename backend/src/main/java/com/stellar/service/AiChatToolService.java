package com.stellar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stellar.entity.SysFile;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.AiResolvedConfig;
import com.stellar.vo.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 聊天工具调用服务：定义 OpenAI 兼容 tools schema + 执行工具（画图/TTS）。
 * <p>由 {@link AiChatService#streamMultiChatWithTools} 在第一次非流式拿到 LLM 的 tool_calls 后调用执行。
 * <p>工具：
 * <ul>
 *   <li>generate_image：调 {@link AiImageService#generateImageSync} 同步生成图片，存 sys_file + 写 sys_ai_image_task</li>
 *   <li>synthesize_speech：按音色/系统开关决定引擎，AI TTS 失败自动降级 Edge TTS，存 sys_file + 写 tts_record</li>
 * </ul>
 * <p>工具结果 content 为 JSON 字符串回传给 LLM（含 status/type/fileId/url 或 status=failed/error）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatToolService {

    private final AiImageService aiImageService;
    private final AiTtsService aiTtsService;
    private final TtsService ttsService;
    private final TtsRecordService ttsRecordService;
    private final AiModelService aiModelService;
    private final SysSettingService sysSettingService;
    private final SysFileMapper fileMapper;
    private final ObjectMapper objectMapper;

    private static final String EDGE_DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    private static final String MIMO_DEFAULT_VOICE = "mimo_default";

    /**
     * 返回 OpenAI 兼容 tools 定义。
     * <p>generate_image 仅在 IMAGE 默认模型可用时暴露；synthesize_speech 恒暴露（Edge 兜底）。
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (imageToolAvailable()) {
            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "generate_image",
                            "description", "生成图片。当用户要求画图、绘图、生成图片、画一张xxx时调用。prompt 用英文或具体描述。",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "prompt", Map.of(
                                                    "type", "string",
                                                    "description", "图片的具体描述提示词(英文/具体)"
                                            )
                                    ),
                                    "required", List.of("prompt")
                            )
                    )
            ));
        }
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "synthesize_speech",
                        "description", "将文字转为语音朗读。当用户要求朗读、读出、用语音回复、读一下时调用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "text", Map.of(
                                                "type", "string",
                                                "description", "要朗读的文字内容"
                                        )
                                ),
                                "required", List.of("text")
                        )
                )
        ));
        return tools;
    }

    /**
     * 执行 LLM 返回的 tool_call。
     *
     * @param toolCall LLM 响应 choices[0].message.tool_calls[i] 节点
     * @param voice    前端传入的音色（可空），用户选了具体音色则按音色所属引擎走覆盖系统开关
     */
    public ToolResult execute(JsonNode toolCall, String voice) {
        String toolCallId = toolCall.path("id").asText("");
        String name = toolCall.path("function").path("name").asText("");
        String argsStr = toolCall.path("function").path("arguments").asText("{}");
        JsonNode args;
        try {
            args = objectMapper.readTree(argsStr);
        } catch (Exception e) {
            log.warn("[AI工具] 解析参数失败 name={} args={}", name, argsStr);
            return new ToolResult(toolCallId, buildErrorContent(name, "参数解析失败: " + e.getMessage()), null, null);
        }
        try {
            return switch (name) {
                case "generate_image" -> generateImage(toolCallId, args.path("prompt").asText(""));
                case "synthesize_speech" -> synthesizeSpeech(toolCallId, args.path("text").asText(""), voice);
                default -> {
                    log.warn("[AI工具] 未知工具: {}", name);
                    yield new ToolResult(toolCallId, buildErrorContent(name, "未知工具"), null, null);
                }
            };
        } catch (Exception e) {
            log.error("[AI工具] 执行失败 name={}: {}", name, e.getMessage(), e);
            return new ToolResult(toolCallId, buildErrorContent(name, e.getMessage()), null, null);
        }
    }

    private ToolResult generateImage(String toolCallId, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return new ToolResult(toolCallId, buildErrorContent("generate_image", "prompt 不能为空"), null, null);
        }
        try {
            Long fileId = aiImageService.generateImageSync(prompt);
            log.info("[AI工具] 画图成功 fileId={} prompt={}", fileId, prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
            return new ToolResult(toolCallId, buildSuccessContent("image", fileId, null), "image", fileId);
        } catch (Exception e) {
            log.warn("[AI工具] 画图失败: {}", e.getMessage());
            return new ToolResult(toolCallId, buildErrorContent("generate_image", e.getMessage()), null, null);
        }
    }

    private ToolResult synthesizeSpeech(String toolCallId, String text, String voice) {
        if (!StringUtils.hasText(text)) {
            return new ToolResult(toolCallId, buildErrorContent("synthesize_speech", "text 不能为空"), null, null);
        }
        boolean useEdge = decideEdgeEngine(voice);
        String actualVoice = resolveVoice(voice, useEdge);
        try {
            if (useEdge) {
                byte[] audio = ttsService.synthesize(text, actualVoice, 1.0, 1.0, 1.0);
                Long fileId = saveAudioFile(text, actualVoice, audio, "mp3", "audio/mpeg");
                log.info("[AI工具] Edge TTS 成功 fileId={} voice={}", fileId, actualVoice);
                return new ToolResult(toolCallId, buildSuccessContent("audio", fileId, actualVoice), "audio", fileId);
            }
            // 优先 AI TTS（解析 AUDIO 默认模型，未配置则抛异常降级 Edge）
            try {
                AiResolvedConfig audioCfg = aiModelService.resolveDefaultConfig("AUDIO");
                byte[] audio = aiTtsService.synthesize(audioCfg.modelId(), text, actualVoice, null);
                Long fileId = saveAudioFile(text, actualVoice, audio, "wav", "audio/wav");
                log.info("[AI工具] AI TTS 成功 fileId={} voice={}", fileId, actualVoice);
                return new ToolResult(toolCallId, buildSuccessContent("audio", fileId, actualVoice), "audio", fileId);
            } catch (Exception aiEx) {
                log.warn("[AI工具] AI TTS 失败，降级 Edge TTS: {}", aiEx.getMessage());
                byte[] audio = ttsService.synthesize(text, EDGE_DEFAULT_VOICE, 1.0, 1.0, 1.0);
                Long fileId = saveAudioFile(text, EDGE_DEFAULT_VOICE, audio, "mp3", "audio/mpeg");
                log.info("[AI工具] 降级 Edge TTS 成功 fileId={}", fileId);
                return new ToolResult(toolCallId, buildSuccessContent("audio", fileId, EDGE_DEFAULT_VOICE), "audio", fileId);
            }
        } catch (Exception e) {
            log.warn("[AI工具] TTS 失败(双引擎均失败): {}", e.getMessage());
            return new ToolResult(toolCallId, buildErrorContent("synthesize_speech", e.getMessage()), null, null);
        }
    }

    /** 决定是否用 Edge 引擎：用户选了具体音色按音色所属引擎走，否则按系统开关 chat_tts_engine */
    private boolean decideEdgeEngine(String voice) {
        if (StringUtils.hasText(voice)) {
            return isEdgeVoice(voice);
        }
        String engine = sysSettingService.get("chat_tts_engine", "ai");
        return "edge".equalsIgnoreCase(engine);
    }

    /** 解析实际音色：用户选了用用户的，否则按引擎取默认（Edge=zh-CN-XiaoxiaoNeural，AI=mimo_default） */
    private String resolveVoice(String voice, boolean useEdge) {
        if (StringUtils.hasText(voice)) {
            return voice;
        }
        return useEdge ? EDGE_DEFAULT_VOICE : MIMO_DEFAULT_VOICE;
    }

    /** Edge 音色 value 形如 zh-CN-XiaoxiaoNeural（xx-Xx-XxxNeural），MiMo 音色为中文名/英文/mimo_default */
    private boolean isEdgeVoice(String voice) {
        return voice != null && voice.matches("^[a-z]{2}-[A-Z][a-z]-\\w+Neural$");
    }

    /** 存 sys_file + 写 tts_record（让 TTS 历史页可见） */
    private Long saveAudioFile(String text, String voice, byte[] audio, String ext, String contentType) {
        SysFile file = new SysFile();
        String name = text.length() > 20 ? text.substring(0, 20) : text;
        file.setOriginalName(name + "." + ext);
        file.setExt(ext);
        file.setContentType(contentType);
        file.setSize((long) audio.length);
        file.setData(audio);
        file.setCreateTime(LocalDateTime.now());
        fileMapper.insert(file);
        ttsRecordService.saveChatTts(text, voice, audio, ext);
        return file.getId();
    }

    private boolean imageToolAvailable() {
        try {
            aiModelService.resolveDefaultConfig("IMAGE");
            return true;
        } catch (Exception e) {
            log.debug("[AI工具] IMAGE 默认模型未配置，generate_image 工具不暴露");
            return false;
        }
    }

    private String buildSuccessContent(String type, Long fileId, String voice) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", "success");
        node.put("type", type);
        node.put("fileId", fileId);
        node.put("url", "/file/" + fileId);
        if (voice != null) {
            node.put("voice", voice);
        }
        return node.toString();
    }

    private String buildErrorContent(String toolName, String error) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", "failed");
        node.put("tool", toolName);
        node.put("error", error);
        return node.toString();
    }
}
