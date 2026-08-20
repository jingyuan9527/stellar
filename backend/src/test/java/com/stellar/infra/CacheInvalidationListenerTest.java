package com.stellar.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link CacheInvalidationListener} 单测：类型化序列化反序列化为 CacheInvalidationMessage 时
 * 转 Spring 事件，非法载荷反序列化异常吞掉。
 */
@ExtendWith(MockitoExtension.class)
class CacheInvalidationListenerTest {

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    Message redisMessage;

    private final Jackson2JsonRedisSerializer<CacheInvalidationMessage> serializer =
            new Jackson2JsonRedisSerializer<>(CacheInvalidationMessage.class);

    @Test
    void onMessage_CacheInvalidationMessage_转事件() {
        CacheInvalidationMessage msg = new CacheInvalidationMessage("kb", "7");
        when(redisMessage.getBody()).thenReturn(serializer.serialize(msg));

        new CacheInvalidationListener(eventPublisher).onMessage(redisMessage, null);

        verify(eventPublisher).publishEvent(new CacheInvalidationEvent("kb", "7"));
    }

    @Test
    void onMessage_非CacheInvalidationMessage_不转事件() {
        when(redisMessage.getBody()).thenReturn("\"hello\"".getBytes(StandardCharsets.UTF_8));

        new CacheInvalidationListener(eventPublisher).onMessage(redisMessage, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onMessage_反序列化异常_吞掉() {
        when(redisMessage.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        new CacheInvalidationListener(eventPublisher).onMessage(redisMessage, null);

        verify(eventPublisher, never()).publishEvent(any());
    }
}