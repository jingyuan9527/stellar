package com.stellar.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

/**
 * RAG 缓存失效订阅器：收到 Redis pub/sub 广播后转成 Spring {@link CacheInvalidationEvent}，
 * 由各业务服务 @EventListener 处理（解耦：本类不依赖具体缓存实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private final ApplicationEventPublisher eventPublisher;
    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Object obj = serializer.deserialize(message.getBody());
            if (obj instanceof CacheInvalidationMessage msg) {
                log.debug("[缓存失效] 收到 scope={} key={}", msg.scope(), msg.key());
                eventPublisher.publishEvent(new CacheInvalidationEvent(msg.scope(), msg.key()));
            }
        } catch (Exception e) {
            log.warn("[缓存失效] 消息处理失败: {}", e.getMessage());
        }
    }
}