package com.stellar.common;

/**
 * Redis key 前缀常量：所有缓存 key 统一以 {@code stellar:} 开头，与其他项目隔离。
 * <p>业务缓存走 Spring Cache（{@code prefixCacheNameWith} 自动加前缀）；
 * 直接用 RedisTemplate 的场景（如限流）应通过本类常量拼接 key。
 */
public final class CacheConstants {

    /** Redis key 统一根前缀 */
    public static final String KEY_PREFIX = "stellar:";

    /** IP 单日限流 key 前缀，完整 key = {@code stellar:rate-limit:{ip}:{date}} */
    public static final String RATE_LIMIT_PREFIX = KEY_PREFIX + "rate-limit:";

    private CacheConstants() {
    }
}
