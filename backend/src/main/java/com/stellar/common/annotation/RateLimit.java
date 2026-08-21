package com.stellar.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口单日限流。标注在 Controller 方法/类上，由 {@code RateLimitInterceptor} 识别，
 * 超限抛 {@code BusinessException(429)}。
 *
 * <p>双档配额：游客按 IP 计数（{@link #daily}），登录用户按 userId 计数
 * （{@link #loginDaily}）——登录不再是免限流通道。
 *
 * @author stellar
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 游客每日允许次数；&lt;=0 表示用全局配置 {@code rate-limit.default-daily}。
     */
    int daily() default -1;

    /**
     * 登录用户每日允许次数；&lt;=0 表示用全局配置 {@code rate-limit.default-user-daily}。
     */
    int loginDaily() default -1;
}
