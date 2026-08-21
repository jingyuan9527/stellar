package com.stellar.infra;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 纯逻辑单元测试：SubjectUtils.getClientIp 的客户端 IP 解析——
 * 可信代理（remoteAddr 命中白名单）按 X-Forwarded-For 首段 &gt; X-Real-IP &gt; remoteAddr；
 * 不可信直连忽略代理头。
 * <p>仅测不依赖 Sa-Token 的 getClientIp；subjectType/subjectId 依赖 StpUtil 静态调用，
 * 需 Sa-Token 上下文，不在本纯逻辑批次覆盖。
 */
class SubjectUtilsTest {

    @AfterEach
    void restoreTrustedProxies() {
        WebUtilsHolder.restore();
    }

    /** 测试间恢复 WebUtils 默认可信代理，避免静态状态串扰 */
    private static final class WebUtilsHolder {
        static void restore() {
            com.stellar.interceptor.WebUtils.configureTrustedProxies("127.0.0.1,::1,0:0:0:0:0:0:0:1");
        }
    }

    private HttpServletRequest mockRequest(String xff, String xri, String remote) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(req.getHeader("X-Real-IP")).thenReturn(xri);
        when(req.getRemoteAddr()).thenReturn(remote);
        return req;
    }

    @Test
    void getClientIp_可信代理_取XForwardedFor首段() {
        HttpServletRequest req = mockRequest("1.1.1.1, 2.2.2.2", "3.3.3.3", "127.0.0.1");
        assertEquals("1.1.1.1", SubjectUtils.getClientIp(req));
    }

    @Test
    void getClientIp_不可信直连_忽略伪造XFF() {
        HttpServletRequest req = mockRequest("1.1.1.1, 2.2.2.2", "3.3.3.3", "9.9.9.9");
        assertEquals("9.9.9.9", SubjectUtils.getClientIp(req));
    }

    @Test
    void getClientIp_XForwardedFor缺失_回退XRealIp() {
        HttpServletRequest req = mockRequest(null, "3.3.3.3", "127.0.0.1");
        assertEquals("3.3.3.3", SubjectUtils.getClientIp(req));
    }

    @Test
    void getClientIp_两者均缺失_回退RemoteAddr() {
        HttpServletRequest req = mockRequest(null, null, "9.9.9.9");
        assertEquals("9.9.9.9", SubjectUtils.getClientIp(req));
    }

    @Test
    void getClientIp_空白头_回退RemoteAddr() {
        HttpServletRequest req = mockRequest("   ", "", "127.0.0.1");
        assertEquals("127.0.0.1", SubjectUtils.getClientIp(req));
    }
}
