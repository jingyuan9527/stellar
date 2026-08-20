package com.stellar.infra;

import com.stellar.common.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * RAG 检索缓存失效广播：数据变更实例失效本地缓存后，通过 Redis pub/sub 通知各实例一起失效，
 * 保证多实例部署下检索索引不陈旧（与 AI 任务通知同一套 Redis 基础设施）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    public static final String CHANNEL = CacheConstants.KEY_PREFIX + "cache:invalidate";

    private final RedisTemplate<String, Object> redisTemplate;

    /** 广播失效消息。失败仅告警：本地缓存已失效，其他实例最多短暂陈旧（Redis 抖动可接受）。 */
    public void publish(String scope, String key) {
        try {
            redisTemplate.convertAndSend(CHANNEL, new CacheInvalidationMessage(scope, key));
            log.debug("[缓存失效] 已广播 scope={} key={}", scope, key);
        } catch (Exception e) {
            log.warn("[缓存失效] 广播失败 scope={} key={}: {}", scope, key, e.getMessage());
        }
    }
}