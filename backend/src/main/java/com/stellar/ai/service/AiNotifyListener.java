package com.stellar.ai.service;

import com.stellar.ai.vo.AiNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;
import com.stellar.infra.SseEmitterManager;

/**
 * AI 任务通知订阅器：收到 Redis pub/sub 消息后，推送给本实例对应 subject 的 SSE 连接。
 * <p>序列化用<b>类级</b> {@link Jackson2JsonRedisSerializer}（按类名定型，不依赖多态 {@code @class}），
 * 与发布端（redisTemplate 关 typing 后的纯 JSON payload）兼容，且与 cacheManager 的安全策略一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotifyListener implements MessageListener {

    private final SseEmitterManager sseEmitterManager;
    private final Jackson2JsonRedisSerializer<AiNotifyMessage> serializer =
            new Jackson2JsonRedisSerializer<>(AiNotifyMessage.class);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            AiNotifyMessage msg = serializer.deserialize(message.getBody());
            if (msg != null) {
                log.debug("[AI通知] 收到 subject={} type={} taskId={} status={}",
                        msg.subject(), msg.type(), msg.taskId(), msg.status());
                sseEmitterManager.send(msg.subject(), "task", msg);
            }
        } catch (Exception e) {
            log.warn("[AI通知] 消息处理失败: {}", e.getMessage());
        }
    }
}
