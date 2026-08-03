package com.stellar.ai.service;

import com.stellar.ai.vo.AiNotifyMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiNotifyPublisher} 单测：Redis pub/sub 广播成功路径 + 异常吞掉分支。
 */
@ExtendWith(MockitoExtension.class)
class AiNotifyPublisherTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    AiNotifyPublisher publisher;

    @Test
    void publish_正常_convertAndSend() {
        AiNotifyMessage msg = new AiNotifyMessage("account:1", "image", 5L, "completed");
        publisher.publish(msg);
        verify(redisTemplate).convertAndSend(AiNotifyPublisher.CHANNEL, msg);
    }

    @Test
    void publish_redis异常_吞掉不抛出() {
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(anyString(), any());
        AiNotifyMessage msg = new AiNotifyMessage("ip:1.2.3.4", "video", 6L, "failed");
        publisher.publish(msg);
        verify(redisTemplate).convertAndSend(anyString(), any());
    }
}
