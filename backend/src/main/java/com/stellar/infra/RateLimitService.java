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
 * 单日限流：基于 Redis INCR + 当日过期，按主体（IP 或用户）+ 日期 key 计数，超限返回 -1。
 * <p>多实例共享计数（替代旧内存实现）。key 格式：{@code stellar:rate-limit:{subject}:{date}}，
 * subject 形如 {@code ip:1.2.3.4} / {@code user:123}。
 * <p>计数与"首次设过期"用一段 Lua 脚本原子执行（单线程保证先 INCR，==1 才 PEXPIRE），
 * 消除旧实现 {@code INCR + expire} 两步衔接处若进程崩溃/故障转移产生的孤儿无 TTL 键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${rate-limit.default-daily:50}")
    private int defaultDaily;

    /** 登录用户默认日配额（注解未显式指定时生效），显著高于游客档 */
    @Value("${rate-limit.default-user-daily:200}")
    private int defaultUserDaily;

    /** 游客主体 key 前缀，用于选择默认配额档 */
    private static final String SUBJECT_IP_PREFIX = "ip:";

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
     * @param subject 限流主体（{@code ip:x.x.x.x} / {@code user:123}）
     * @param daily   指定阈值；&lt;=0 按主体类型取全局默认（游客档/用户档）
     * @return 当前次数；-1 表示超限或 Redis 不可用
     */
    public int tryIncr(String subject, int daily) {
        int fallback = subject.startsWith(SUBJECT_IP_PREFIX) ? defaultDaily : defaultUserDaily;
        int limit = daily > 0 ? daily : fallback;
        String key = CacheConstants.RATE_LIMIT_PREFIX + subject + ":" + LocalDate.now();
        try {
            Long now = stringRedisTemplate.execute(INCR_WITH_TTL, Collections.singletonList(key));
            if (now == null) {
                log.warn("[限流] Redis 自增失败，subject={} key={}", subject, key);
                return -1;
            }
            if (now > limit) {
                log.warn("[限流] subject={} 今日第 {} 次，超限 {}", subject, now, limit);
                return -1;
            }
            return now.intValue();
        } catch (Exception e) {
            log.warn("[限流] Lua 执行异常，subject={} key={}: {}", subject, key, e.getMessage());
            return -1;
        }
    }
}