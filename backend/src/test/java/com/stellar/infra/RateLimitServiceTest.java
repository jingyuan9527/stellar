package com.stellar.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitService} 单测：用 Mockito 隔离 Redis（StringRedisTemplate），
 * 验证单日限流的 Lua 原子自增+设 TTL、超限返回 -1、Redis 失败降级、游客/用户双档默认阈值兜底等核心分支。
 * <p>INCR + PEXPIRE 由单段 Lua 脚本执行，语义上"首次 INCR==1 时设置当日 TTL"，测试只验证脚本调用与返回值。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(stringRedisTemplate);
        // @Value 在单元测试里不会被注入，手动把默认日限额设为与生产一致的 50 / 200
        setField("defaultDaily", 50);
        setField("defaultUserDaily", 200);
    }

    private void setField(String name, int v) {
        try {
            Field f = RateLimitService.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(rateLimitService, v);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubScriptResult(Long v) {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(v);
    }

    @Test
    void tryIncr_首次自增_返回1() {
        stubScriptResult(1L);
        assertEquals(1, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_非首次未超限_返回当前次数() {
        stubScriptResult(3L);
        assertEquals(3, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_超过阈值_返回负1() {
        stubScriptResult(6L); // > 5
        assertEquals(-1, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_边界等于阈值_不超限() {
        stubScriptResult(5L);
        assertEquals(5, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_Redis返回null_降级返回负1() {
        stubScriptResult(null);
        assertEquals(-1, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_Redis执行异常_降级返回负1() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RuntimeException("boom"));
        assertEquals(-1, rateLimitService.tryIncr("ip:1.2.3.4", 5));
    }

    @Test
    void tryIncr_daily为负_用默认阈值且未超限() {
        stubScriptResult(10L); // <= 50
        assertEquals(10, rateLimitService.tryIncr("ip:1.2.3.4", -1));
    }

    @Test
    void tryIncr_daily为负_默认阈值也会超限() {
        stubScriptResult(51L); // > 50
        assertEquals(-1, rateLimitService.tryIncr("ip:1.2.3.4", -1));
    }

    @Test
    void tryIncr_用户主体_daily为负_用用户档默认阈值() {
        stubScriptResult(150L); // <= 200（用户档）
        assertEquals(150, rateLimitService.tryIncr("user:1", -1));
    }

    @Test
    void tryIncr_用户主体_超用户档阈值_返回负1() {
        stubScriptResult(201L); // > 200
        assertEquals(-1, rateLimitService.tryIncr("user:1", -1));
    }

    @Test
    void tryIncr_传入自定义阈值_不调用默认() {
        stubScriptResult(3L);
        assertEquals(3, rateLimitService.tryIncr("ip:1.2.3.4", 5));
        verify(stringRedisTemplate, never()).expire(any(), any());
    }
}