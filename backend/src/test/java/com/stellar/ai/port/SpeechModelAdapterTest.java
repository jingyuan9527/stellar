package com.stellar.ai.port;

import com.stellar.ai.service.AiModelService;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link SpeechModelAdapter} 单测：AUDIO 模型解析与类型校验、token 用量记录透传。
 */
@ExtendWith(MockitoExtension.class)
class SpeechModelAdapterTest {

    @Mock
    AiModelService aiModelService;
    @Mock
    SysAiUsageService sysAiUsageService;

    @Test
    void resolveAudioModel_AUDIO模型_返回中立配置() {
        when(aiModelService.resolveConfig(1L))
                .thenReturn(new AiResolvedConfig(1L, 2L, "http://tts.test/", "key", "tts-model", "AUDIO"));
        SpeechModelAdapter adapter = new SpeechModelAdapter(aiModelService, sysAiUsageService);
        var cfg = adapter.resolveAudioModel(1L);
        assertEquals(2L, cfg.providerId());
        assertEquals("http://tts.test/", cfg.endpoint());
        assertEquals("key", cfg.apiKey());
        assertEquals("tts-model", cfg.model());
    }

    @Test
    void resolveAudioModel_非AUDIO_抛() {
        when(aiModelService.resolveConfig(1L))
                .thenReturn(new AiResolvedConfig(1L, 2L, "http://tts.test/", "key", "txt-model", "TEXT"));
        SpeechModelAdapter adapter = new SpeechModelAdapter(aiModelService, sysAiUsageService);
        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.resolveAudioModel(1L));
        assertTrue(ex.getMessage().contains("语音合成类型"));
    }

    @Test
    void recordUsage_透传记token() {
        SpeechModelAdapter adapter = new SpeechModelAdapter(aiModelService, sysAiUsageService);
        adapter.recordUsage("account", "u1", 2L, "tts-model", 5, 0, 5, "estimate");
        verify(sysAiUsageService).record("account", "u1", 2L, "tts-model", "AUDIO", 5, 0, 5, "estimate");
    }
}