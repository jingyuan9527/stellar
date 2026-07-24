package com.stellar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 纯 JDK 内存 IP 限流：按 IP + 当日日期 计数，每日按日期 key 自然隔离重置。
 * <p>单机够用；多实例部署需换 Redis 实现（见 AGENTS.md）。
 */
@Slf4j
@Service
public class RateLimitService {

    @Value("${rate-limit.default-daily:50}")
    private int defaultDaily;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * 自增并判断是否超限。
     *
     * @param ip    客户端 IP
     * @param daily 指定阈值；&lt;=0 用全局默认
     * @return 当前次数；-1 表示超限
     */
    public int tryIncr(String ip, int daily) {
        int limit = daily > 0 ? daily : defaultDaily;
        String key = ip + ":" + LocalDate.now();
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int now = counter.incrementAndGet();
        if (now > limit) {
            log.warn("[限流] IP={} 今日第 {} 次，超限 {}", ip, now, limit);
            return -1;
        }
        return now;
    }

    /**
     * 清理非今日的过期计数，回收内存。可由定时任务调用。
     */
    public void cleanup() {
        String today = LocalDate.now().toString();
        counters.keySet().removeIf(k -> !k.endsWith(today));
    }
}
