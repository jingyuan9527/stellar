package com.stellar.ai.service;

import com.stellar.common.CacheConstants;
import com.stellar.ai.vo.AiNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 任务通知发布器：通过 Redis pub/sub 广播任务完成消息，
 * 各实例的 AiNotifyListener 订阅后推送给本实例的 SSE 连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotifyPublisher {

    public static final String CHANNEL = CacheConstants.KEY_PREFIX + "ai:notify";

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(AiNotifyMessage msg) {
        try {
            redisTemplate.convertAndSend(CHANNEL, msg);
            log.debug("[AI通知] 已发布 subject={} type={} taskId={} status={}",
                    msg.subject(), msg.type(), msg.taskId(), msg.status());
        } catch (Exception e) {
            log.warn("[AI通知] 发布失败: {}", e.getMessage());
        }
    }
}
