package com.stellar.tts.port;

import com.stellar.common.BusinessException;

/**
 * AI 语音模型缝：tts 只依赖本接口，不直接依赖 ai 模块；
 * 由 ai 模块提供实现（解析 AUDIO 类型模型配置 + 记录 token 用量）。
 */
public interface SpeechModelPort {

    /**
     * 按 modelId 解析 AUDIO 类型模型配置；非 AUDIO 或不存在时抛 {@link BusinessException}。
     */
    SpeechModelConfig resolveAudioModel(Long modelId);

    /**
     * 记录一次语音合成的 token 消费。
     *
     * @param source 取值来源：usage（上游精确值）/ estimate（字符估算）
     */
    void recordUsage(String subjectType, String subjectId, Long providerId, String model,
                     int promptTokens, int completionTokens, int totalTokens, String source);
}
