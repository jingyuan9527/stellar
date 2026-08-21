package com.stellar.tts.port;

/**
 * 语音合成模型配置（tts 模块中立视图）：由 ai 模块按 modelId 解析并校验 AUDIO 类型后提供。
 */
public record SpeechModelConfig(
        Long providerId,
        String endpoint,
        String apiKey,
        String model
) {
}
