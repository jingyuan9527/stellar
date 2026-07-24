package com.stellar.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注游客（未登录）可访问的接口。
 *
 * <p>由 {@code AuthInterceptor} 识别，命中即跳过 Sa-Token 登录校验；
 * 方法内部可用 {@code StpUtil.isLogin()} 区分游客与登录用户，做差异化处理（如限流、额度）。
 * <p>默认所有接口仍要求登录，只有显式标注本注解的 Controller 方法/类才对游客开放。
 *
 * @author stellar
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicAccess {
}
