package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiChatStreamDTO {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "消息不能为空")
    private String userMessage;

    /** 可选：TEXT 模型 ID，为空用默认 */
    private Long modelId;

    /** 可选：TTS 音色（聊天工具调用语音合成时用）；为空按系统开关 chat_tts_engine 兜底。
     * <p>Edge 音色形如 zh-CN-XiaoxiaoNeural，MiMo 音色形如 冰糖/mimo_default。
     * 用户选了具体音色则按音色所属引擎走，覆盖系统开关。 */
    private String voice;
}
