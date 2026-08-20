package com.stellar.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

/**
 * RAG 缓存失效订阅器：收到 Redis pub/sub 广播后转成 Spring {@link CacheInvalidationEvent}，
 * 由各业务服务 @EventListener 处理（解耦：本类不依赖具体缓存实现）。
 * <p>序列化用<b>类级</b> {@link Jackson2JsonRedisSerializer}（按类名定型，不依赖多态 {@code @class}），
 * 与发布端（redisTemplate 关 typing 后的纯 JSON payload）兼容，且与 cacheManager 的安全策略一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private final ApplicationEventPublisher eventPublisher;
    private final Jackson2JsonRedisSerializer<CacheInvalidationMessage> serializer =
            new Jackson2JsonRedisSerializer<>(CacheInvalidationMessage.class);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CacheInvalidationMessage msg = serializer.deserialize(message.getBody());
            if (msg != null) {
                log.debug("[缓存失效] 收到 scope={} key={}", msg.scope(), msg.key());
                eventPublisher.publishEvent(new CacheInvalidationEvent(msg.scope(), msg.key()));
            }
        } catch (Exception e) {
            log.warn("[缓存失效] 消息处理失败: {}", e.getMessage());
        }
    }
}