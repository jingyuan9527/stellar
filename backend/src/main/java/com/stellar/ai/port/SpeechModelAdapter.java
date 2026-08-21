package com.stellar.ai.port;

import com.stellar.ai.service.AiModelService;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.tts.port.SpeechModelConfig;
import com.stellar.tts.port.SpeechModelPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SpeechModelPort} 的 ai 实现：解析 AUDIO 类型模型配置 + 记录 token 用量，
 * 让 tts 模块无需编译期依赖 ai。
 */
@Component
@RequiredArgsConstructor
public class SpeechModelAdapter implements SpeechModelPort {

    private final AiModelService aiModelService;
    private final SysAiUsageService sysAiUsageService;

    @Override
    public SpeechModelConfig resolveAudioModel(Long modelId) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"AUDIO".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是语音合成类型，请选择 AUDIO 类型模型");
        }
        return new SpeechModelConfig(cfg.providerId(), cfg.endpoint(), cfg.apiKey(), cfg.model());
    }

    @Override
    public void recordUsage(String subjectType, String subjectId, Long providerId, String model,
                            int promptTokens, int completionTokens, int totalTokens, String source) {
        sysAiUsageService.record(subjectType, subjectId, providerId, model, "AUDIO",
                promptTokens, completionTokens, totalTokens, source);
    }
}
