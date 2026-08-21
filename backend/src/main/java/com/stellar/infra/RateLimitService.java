package com.stellar.infra;

import com.stellar.common.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;

/**
 * IP 单日限流：基于 Redis INCR + 当日过期，按 IP + 日期 key 计数，超限返回 -1。
 * <p>多实例共享计数（替代旧内存实现）。key 格式：{@code stellar:rate-limit:{ip}:{date}}。
 * <p>计数与"首次设过期"用一段 Lua 脚本原子执行（单线程保证先 INCR，==1 才 PEXPIRE），
 * 消除旧实现 {@code INCR + expire} 两步衔接处若进程崩溃/故障转移产生的孤儿无 TTL 键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${rate-limit.default-daily:50}")
    private int defaultDaily;

    /** 当日毫秒数，配合 PEXPIRE 设键存活。 */
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            "local now = redis.call('INCR', KEYS[1])\n" +
            "if now == 1 then\n" +
            "  redis.call('PEXPIRE', KEYS[1], " + DAY_MS + ")\n" +
            "end\n" +
            "return now",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 自增并判断是否超限。
     *
     * @param ip    客户端 IP
     * @param daily 指定阈值；&lt;=0 用全局默认
     * @return 当前次数；-1 表示超限或 Redis 不可用
     */
    public int tryIncr(String ip, int daily) {
        int limit = daily > 0 ? daily : defaultDaily;
        String key = CacheConstants.RATE_LIMIT_PREFIX + ip + ":" + LocalDate.now();
        try {
            Long now = stringRedisTemplate.execute(INCR_WITH_TTL, Collections.singletonList(key));
            if (now == null) {
                log.warn("[限流] Redis 自增失败，IP={} key={}", ip, key);
                return -1;
            }
            if (now > limit) {
                log.warn("[限流] IP={} 今日第 {} 次，超限 {}", ip, now, limit);
                return -1;
            }
            return now.intValue();
        } catch (Exception e) {
            log.warn("[限流] Lua 执行异常，IP={} key={}: {}", ip, key, e.getMessage());
            return -1;
        }
    }
}