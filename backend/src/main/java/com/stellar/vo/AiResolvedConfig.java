package com.stellar.vo;

/**
 * 解析后的 AI 调用配置：按 modelId 解析出供应商 endpoint/apiKey + 模型名 + 类型。
 * <p>供 AiChatService/AiImageService 等调用方直接发起 LLM 请求，无需各自查两张表。
 */
public record AiResolvedConfig(
        Long modelId,
        Long providerId,
        String endpoint,
        String apiKey,
        String model,
        String modelType
) {
}
