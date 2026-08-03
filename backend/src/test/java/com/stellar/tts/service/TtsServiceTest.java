package com.stellar.tts.service;

import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link TtsService} 单测：仅覆盖公开方法 synthesize 的入参校验守护分支
 * （文本为空 / 音色为 null / 音色格式不符）。合法调用会发起 Edge WebSocket 真实连接，不在单测范围。
 */
@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    ExternalCallLogger externalCallLogger;

    TtsService service;

    @BeforeEach
    void setup() {
        service = new TtsService(externalCallLogger);
    }

    @Test
    void synthesize_文本为空_抛() {
        assertThrows(BusinessException.class, () -> service.synthesize("  ", "zh-CN-XiaoxiaoNeural", 1.0, 1.0, 1.0));
    }

    @Test
    void synthesize_音色为null_抛() {
        assertThrows(BusinessException.class, () -> service.synthesize("你好", null, 1.0, 1.0, 1.0));
    }

    @Test
    void synthesize_音色格式不符_抛() {
        assertThrows(BusinessException.class, () -> service.synthesize("你好", "bad-voice", 1.0, 1.0, 1.0));
    }
}
