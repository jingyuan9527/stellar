package com.stellar.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SETNX 互斥锁：防并发重入（如定时与手动同步重叠）。
 * <p>Redis 异常时放行执行——锁是防重入优化而非正确性依赖，不因锁故障阻塞业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMutex {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 抢占锁；true=获得。Redis 异常放行返回 true。 */
    public boolean tryAcquire(String key, Duration ttl) {
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("[RedisMutex] 加锁失败放行 key={}: {}", key, e.getMessage());
            return true;
        }
    }

    /** 释放锁；异常仅告警。 */
    public void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[RedisMutex] 释放锁失败 key={}: {}", key, e.getMessage());
        }
    }
}