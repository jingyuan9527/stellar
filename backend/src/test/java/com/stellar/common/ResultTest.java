package com.stellar.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 纯逻辑单元测试：Result 响应信封的构造语义。
 */
class ResultTest {

    @Test
    void success_默认_200且无数据() {
        Result<String> r = Result.success();
        assertEquals(200, r.getCode());
        assertEquals("操作成功", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void success_带数据() {
        Result<String> r = Result.success("x");
        assertEquals(200, r.getCode());
        assertEquals("x", r.getData());
    }

    @Test
    void failed_仅消息_默认500() {
        Result<?> r = Result.failed("boom");
        assertEquals(500, r.getCode());
        assertEquals("boom", r.getMessage());
    }

    @Test
    void failed_自定义码与消息() {
        Result<?> r = Result.failed(400, "bad");
        assertEquals(400, r.getCode());
        assertEquals("bad", r.getMessage());
    }

    @Test
    void failed_枚举() {
        Result<?> r = Result.failed(ResultCode.NOT_FOUND);
        assertEquals(404, r.getCode());
        assertEquals("资源不存在", r.getMessage());
    }
}
