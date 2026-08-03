package com.stellar.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RateLimitService} 单测：用 Mockito 隔离 Redis（StringRedisTemplate），
 * 验证 IP 单日限流的计数、首增过期、超限返回 -1、Redis 失败降级、默认阈值兜底等核心分支。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimitService = new RateLimitService(stringRedisTemplate);
        // @Value 在单元测试里不会被注入，手动把默认日限额设为与生产一致的 50
        setDefaultDaily(50);
    }

    private void setDefaultDaily(int v) {
        try {
            Field f = RateLimitService.class.getDeclaredField("defaultDaily");
            f.setAccessible(true);
            f.set(rateLimitService, v);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void tryIncr_首次自增_设置当日过期_返回1() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        int r = rateLimitService.tryIncr("1.2.3.4", 5);
        assertEquals(1, r);
        verify(stringRedisTemplate).expire(anyString(), eq(Duration.ofDays(1)));
    }

    @Test
    void tryIncr_非首次未超限_不重复设置过期_返回当前次数() {
        when(valueOps.increment(anyString())).thenReturn(3L);
        assertEquals(3, rateLimitService.tryIncr("1.2.3.4", 5));
        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void tryIncr_超过阈值_返回负1() {
        when(valueOps.increment(anyString())).thenReturn(6L); // > 5
        assertEquals(-1, rateLimitService.tryIncr("1.2.3.4", 5));
    }

    @Test
    void tryIncr_边界等于阈值_不超限() {
        when(valueOps.increment(anyString())).thenReturn(5L);
        assertEquals(5, rateLimitService.tryIncr("1.2.3.4", 5));
    }

    @Test
    void tryIncr_Redis自增失败返回null_降级返回负1() {
        when(valueOps.increment(anyString())).thenReturn(null);
        assertEquals(-1, rateLimitService.tryIncr("1.2.3.4", 5));
    }

    @Test
    void tryIncr_daily为负_用默认阈值且未超限() {
        when(valueOps.increment(anyString())).thenReturn(10L); // <= 50
        assertEquals(10, rateLimitService.tryIncr("1.2.3.4", -1));
    }

    @Test
    void tryIncr_daily为负_默认阈值也会超限() {
        when(valueOps.increment(anyString())).thenReturn(51L); // > 50
        assertEquals(-1, rateLimitService.tryIncr("1.2.3.4", -1));
    }
}
