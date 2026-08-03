package com.stellar.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 纯逻辑单元测试：BusinessException 的 code 取值语义。
 */
class BusinessExceptionTest {

    @Test
    void 仅消息_默认500() {
        BusinessException e = new BusinessException("x");
        assertEquals(500, e.getCode());
        assertEquals("x", e.getMessage());
    }

    @Test
    void 自定义码与消息() {
        BusinessException e = new BusinessException(429, "too many");
        assertEquals(429, e.getCode());
        assertEquals("too many", e.getMessage());
    }

    @Test
    void 枚举构造() {
        BusinessException e = new BusinessException(ResultCode.UNAUTHORIZED);
        assertEquals(401, e.getCode());
        assertEquals("未登录或登录已过期", e.getMessage());
    }
}
