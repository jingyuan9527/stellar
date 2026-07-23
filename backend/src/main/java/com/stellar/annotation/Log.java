package com.stellar.annotation;

import com.stellar.enums.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 {@code LogAspect} 切面统一记录。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /**
     * 模块名称，如 "用户管理"、"认证管理"
     */
    String title() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;
}
