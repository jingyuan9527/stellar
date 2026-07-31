package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.system.entity.SysFile;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.ai.vo.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.stellar.system.service.SysSettingService;
import com.stellar.tts.service.AiTtsService;
import com.stellar.tts.service.TtsRecordService;
import com.stellar.tts.service.TtsService;

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
        boolean imageAvailable = imageToolAvailable();
        if (imageAvailable) {
            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "generate_image",
                            "description", "生成图片。用户想要任何视觉图像时必须调用此工具，不要用文字描述代替。触发场景包括但不限于：画图/绘图/生成图片/画一张/来一张/来张/配图/插图/壁纸/头像/照片/海报/封面/看看xx的样子/xx长什么样/给我画/帮我画/能不能画/做个图。用户用任何语言表达想要图像的意图时都应主动调用。",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "prompt", Map.of(
                                                    "type", "string",
                                                    "description", "图片的视觉描述，建议英文，包含主体/动作/场景/风格/色彩，越具体越好"
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
                        "description", "文字转语音。用户想要听到语音/音频时必须调用此工具，不要只用文字回复代替。触发场景包括但不限于：朗读/读出/读一下/念给我听/说给我听/用语音回复/发个语音说xx/配音/播报/开口说话/给段语音/祝福语音/念一段/读这段/语音。用户表达想要听声音/语音的任何意图时都应主动调用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "text", Map.of(
                                                "type", "string",
                                                "description", "要朗读的完整文字内容"
                                        )
                                ),
                                "required", List.of("text")
                        )
                )
        ));
        log.info("[AI工具] 暴露工具 count={} imageAvailable={}", tools.size(), imageAvailable);
        return tools;
    }

    /**
     * 执行 LLM 返回的 tool_call。
     *
     * @param toolCall LLM 响应 choices[0].message.tool_calls[i] 节点
     * @param voice    前端传入的音色（可空），用户选了具体音色则按音色所属引擎走覆盖系统开关
     * @param subjectType 主体类型（account/ip，调用方在同步阶段捕获传入，异步线程无 web 上下文）
     * @param subjectId 主体 ID
     */
    public ToolResult execute(JsonNode toolCall, String voice, String subjectType, String subjectId) {
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
                case "generate_image" -> generateImage(toolCallId, args.path("prompt").asText(""), subjectType, subjectId);
                case "synthesize_speech" -> synthesizeSpeech(toolCallId, args.path("text").asText(""), voice, subjectType, subjectId);
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

    private ToolResult generateImage(String toolCallId, String prompt, String subjectType, String subjectId) {
        if (!StringUtils.hasText(prompt)) {
            return new ToolResult(toolCallId, buildErrorContent("generate_image", "prompt 不能为空"), null, null);
        }
        try {
            Long fileId = aiImageService.generateImageSync(prompt, subjectType, subjectId);
            log.info("[AI工具] 画图成功 fileId={} prompt={}", fileId, prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
            return new ToolResult(toolCallId, buildSuccessContent("image", fileId, null), "image", fileId);
        } catch (Exception e) {
            log.warn("[AI工具] 画图失败: {}", e.getMessage());
            return new ToolResult(toolCallId, buildErrorContent("generate_image", e.getMessage()), null, null);
        }
    }

    private ToolResult synthesizeSpeech(String toolCallId, String text, String voice, String subjectType, String subjectId) {
        if (!StringUtils.hasText(text)) {
            return new ToolResult(toolCallId, buildErrorContent("synthesize_speech", "text 不能为空"), null, null);
        }
        boolean useEdge = decideEdgeEngine(voice);
        String actualVoice = resolveVoice(voice, useEdge);
        try {
            if (useEdge) {
                byte[] audio = ttsService.synthesize(text, actualVoice, 1.0, 1.0, 1.0);
                Long fileId = saveAudioFile(text, actualVoice, audio, "mp3", "audio/mpeg", subjectType, subjectId);
                log.info("[AI工具] Edge TTS 成功 fileId={} voice={}", fileId, actualVoice);
                return new ToolResult(toolCallId, buildSuccessContent("audio", fileId, actualVoice), "audio", fileId);
            }
            // 优先 AI TTS（解析 AUDIO 默认模型，未配置则抛异常降级 Edge）
            try {
                AiResolvedConfig audioCfg = aiModelService.resolveDefaultConfig("AUDIO");
                byte[] audio = aiTtsService.synthesize(audioCfg.modelId(), text, actualVoice, null);
                Long fileId = saveAudioFile(text, actualVoice, audio, "wav", "audio/wav", subjectType, subjectId);
                log.info("[AI工具] AI TTS 成功 fileId={} voice={}", fileId, actualVoice);
                return new ToolResult(toolCallId, buildSuccessContent("audio", fileId, actualVoice), "audio", fileId);
            } catch (Exception aiEx) {
                log.warn("[AI工具] AI TTS 失败，降级 Edge TTS: {}", aiEx.getMessage());
                byte[] audio = ttsService.synthesize(text, EDGE_DEFAULT_VOICE, 1.0, 1.0, 1.0);
                Long fileId = saveAudioFile(text, EDGE_DEFAULT_VOICE, audio, "mp3", "audio/mpeg", subjectType, subjectId);
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
        return voice != null && voice.matches(
                "^[a-z]{2,3}-[A-Z]{2}(?:-[a-z0-9]+)*-[A-Za-z][A-Za-z0-9]{0,63}Neural$");
    }

    /** 存 sys_file + 写 tts_record（让 TTS 历史页可见） */
    private Long saveAudioFile(String text, String voice, byte[] audio, String ext, String contentType,
                               String subjectType, String subjectId) {
        SysFile file = new SysFile();
        String name = text.length() > 20 ? text.substring(0, 20) : text;
        file.setOriginalName(name + "." + ext);
        file.setExt(ext);
        file.setContentType(contentType);
        file.setSize((long) audio.length);
        file.setData(audio);
        file.setCreateTime(LocalDateTime.now());
        fileMapper.insert(file);
        ttsRecordService.saveChatTts(text, voice, audio, ext, subjectType, subjectId);
        return file.getId();
    }

    private boolean imageToolAvailable() {
        try {
            return !aiModelService.listEnabledByType("IMAGE").isEmpty();
        } catch (Exception e) {
            log.debug("[AI工具] 查询 IMAGE 模型失败，generate_image 工具不暴露");
            return false;
        }
    }

    /** 工具结果回传 LLM：用自然语言描述，避免 LLM 直接复制 JSON 作为回复 */
    private String buildSuccessContent(String type, Long fileId, String voice) {
        if ("image".equals(type)) {
            return "图片已成功生成，用户可在消息中直接查看。请用自然语言简要告知用户结果。";
        }
        return "语音已成功合成，用户可在消息中直接收听。请用自然语言简要告知用户结果。";
    }

    private String buildErrorContent(String toolName, String error) {
        if ("generate_image".equals(toolName)) {
            return "图片生成失败：" + error + "。请用自然语言向用户致歉并说明原因。";
        }
        return "语音合成失败：" + error + "。请用自然语言向用户致歉并说明原因。";
    }
}
