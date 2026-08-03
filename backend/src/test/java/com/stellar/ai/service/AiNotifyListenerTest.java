package com.stellar.ai.service;

import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.infra.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiNotifyListener} 单测：反序列化为 AiNotifyMessage 时推送 SSE，
 * 其他类型不推送，反序列化异常吞掉。
 */
@ExtendWith(MockitoExtension.class)
class AiNotifyListenerTest {

    @Mock
    SseEmitterManager sseEmitterManager;

    @Mock
    Message redisMessage;

    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    void onMessage_AiNotifyMessage_推送SSE() {
        AiNotifyMessage msg = new AiNotifyMessage("account:3", "image", 7L, "completed");
        when(redisMessage.getBody()).thenReturn(serializer.serialize(msg));

        new AiNotifyListener(sseEmitterManager).onMessage(redisMessage, null);

        verify(sseEmitterManager).send(eq("account:3"), eq("task"), any(AiNotifyMessage.class));
    }

    @Test
    void onMessage_非AiNotifyMessage_不推送() {
        when(redisMessage.getBody()).thenReturn(serializer.serialize("hello"));

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
