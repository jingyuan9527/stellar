package com.stellar.test;

import java.lang.reflect.Field;

/**
 * 测试辅助：把服务里「字段初始化、非注入」的 final 依赖（如 HttpClient）替换为 mock，
 * 以便对内含网络调用的纯逻辑做单测。仅在测试代码中使用。
 * <p>注：JDK 12+ 已移除 {@code Field.modifiers} 字段，对「非常量 final 实例字段」直接用
 * setAccessible + set 即可（JIT 不会对非编译期常量做 final 折叠）。
 */
public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static void setFinalField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("无法注入字段 " + fieldName, e);
        }
    }
}
