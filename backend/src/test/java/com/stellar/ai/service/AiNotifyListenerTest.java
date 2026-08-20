package com.stellar.ai.service;

import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.infra.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiNotifyListener} 单测：类型化序列化反序列化为 AiNotifyMessage 时推送 SSE，
 * 非 AiNotifyMessage 载荷/非法 JSON 反序列化异常吞掉。
 */
@ExtendWith(MockitoExtension.class)
class AiNotifyListenerTest {

    @Mock
    SseEmitterManager sseEmitterManager;

    @Mock
    Message redisMessage;

    private final Jackson2JsonRedisSerializer<AiNotifyMessage> serializer =
            new Jackson2JsonRedisSerializer<>(AiNotifyMessage.class);

    @Test
    void onMessage_AiNotifyMessage_推送SSE() {
        AiNotifyMessage msg = new AiNotifyMessage("account:3", "image", 7L, "completed");
        when(redisMessage.getBody()).thenReturn(serializer.serialize(msg));

        new AiNotifyListener(sseEmitterManager).onMessage(redisMessage, null);

        verify(sseEmitterManager).send(eq("account:3"), eq("task"), any(AiNotifyMessage.class));
    }

    @Test
    void onMessage_非AiNotifyMessage_不推送() {
        when(redisMessage.getBody()).thenReturn("\"hello\"".getBytes(StandardCharsets.UTF_8));

        new AiNotifyListener(sseEmitterManager).onMessage(redisMessage, null);

        verify(sseEmitterManager, never()).send(anyString(), anyString(), any());
    }

    @Test
    void onMessage_反序列化异常_吞掉() {
        when(redisMessage.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        new AiNotifyListener(sseEmitterManager).onMessage(redisMessage, null);

        verify(sseEmitterManager, never()).send(anyString(), anyString(), any());
    }
}
