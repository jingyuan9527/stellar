package com.stellar.service;

import com.stellar.vo.AiNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

/**
 * AI 任务通知订阅器：收到 Redis pub/sub 消息后，推送给本实例对应 subject 的 SSE 连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotifyListener implements MessageListener {

    private final SseEmitterManager sseEmitterManager;
    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Object obj = serializer.deserialize(message.getBody());
            if (obj instanceof AiNotifyMessage msg) {
                log.debug("[AI通知] 收到 subject={} type={} taskId={} status={}",
                        msg.subject(), msg.type(), msg.taskId(), msg.status());
                sseEmitterManager.send(msg.subject(), "task", msg);
            }
        } catch (Exception e) {
            log.warn("[AI通知] 消息处理失败: {}", e.getMessage());
        }
    }
}
