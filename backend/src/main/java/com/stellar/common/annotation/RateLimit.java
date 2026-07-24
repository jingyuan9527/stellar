package com.stellar.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口 IP 单日限流。标注在 Controller 方法/类上，由 {@code RateLimitInterceptor} 识别，
 * 按客户端 IP 单日累计计数，超限抛 {@code BusinessException(429)}。
 *
 * <p>通常与 {@link PublicAccess} 叠加用于对游客开放的耗资源接口（如语音合成）；
 * 纯展示类公开接口（如橱窗列表）无需标注。
 *
 * @author stellar
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 每日允许次数；-1 表示用全局配置 {@code rate-limit.default-daily}。
     */
    int daily() default -1;
}
