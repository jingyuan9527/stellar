package com.stellar.infra;

import com.stellar.common.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * IP 单日限流：基于 Redis INCR + 当日过期，按 IP + 日期 key 计数，超限返回 -1。
 * <p>多实例共享计数（替代旧内存实现）。key 格式：{@code stellar:rate-limit:{ip}:{date}}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${rate-limit.default-daily:50}")
    private int defaultDaily;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 自增并判断是否超限。
     *
     * @param ip    客户端 IP
     * @param daily 指定阈值；&lt;=0 用全局默认
     * @return 当前次数；-1 表示超限
     */
    public int tryIncr(String ip, int daily) {
        int limit = daily > 0 ? daily : defaultDaily;
        String key = CacheConstants.RATE_LIMIT_PREFIX + ip + ":" + LocalDate.now();
        Long now = stringRedisTemplate.opsForValue().increment(key);
        if (now == null) {
            log.warn("[限流] Redis 自增失败，IP={} key={}", ip, key);
            return -1;
        }
        // 首次自增时设置当日过期，次日自动清理
        if (now == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(1));
        }
        if (now > limit) {
            log.warn("[限流] IP={} 今日第 {} 次，超限 {}", ip, now, limit);
            return -1;
        }
        return now.intValue();
    }
}
