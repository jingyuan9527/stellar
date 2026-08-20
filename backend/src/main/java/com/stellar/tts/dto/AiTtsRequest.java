package com.stellar.tts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 语音合成请求参数（MiMo TTS 预置音色模式）。
 * <p>走 OpenAI 兼容的 /v1/chat/completions + audio 参数，合成文本放 assistant 消息，
 * 风格指令放 user 消息（可空）。
 */
@Data
public class AiTtsRequest {

    /** AUDIO 类型模型 ID（ai/config 配置的供应商模型） */
    @NotNull(message = "模型不能为空")
    private Long modelId;

    /** 合成文本（放在 role=assistant 消息中） */
    @NotBlank(message = "合成文本不能为空")
    @Size(max = 2000, message = "文本长度不能超过2000字")
    private String text;

    /** 预置音色，如 冰糖/Chloe/mimo_default */
    @NotBlank(message = "音色不能为空")
    private String voice;

    /** 风格指令（可选，自然语言描述放 user 消息；为空则 user 消息给空串） */
    private String style;
}
