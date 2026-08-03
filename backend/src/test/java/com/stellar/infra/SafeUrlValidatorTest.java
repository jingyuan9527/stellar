package com.stellar.infra;

import com.stellar.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 纯逻辑单元测试：SafeUrlValidator 的 SSRF 防护与限长读取。
 * <p>IP 分类通过 IP 字面量 URL（如 http://127.0.0.1）走 validatePublicHttpUrl 离线验证，
 * 不触发真实 DNS 解析，确保测试确定性。
 */
class SafeUrlValidatorTest {

    private static final String P = "测试";

    @Test
    void validatePublicHttpUrl_非法格式_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("not a url", P));
    }

    @Test
    void validatePublicHttpUrl_非Http协议_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("ftp://example.com/x", P));
    }

    @Test
    void validatePublicHttpUrl_带UserInfo_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://user@example.com/x", P));
    }

    @Test
    void validatePublicHttpUrl_localhost_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://localhost/x", P));
    }

    // ===== IP 字面量分类（无 DNS，确定性）=====

    @Test
    void validatePublicHttpUrl_回环地址_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://127.0.0.1/x", P));
    }

    @Test
    void validatePublicHttpUrl_私有地址_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://10.0.0.5/x", P));
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://192.168.1.1/x", P));
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://172.16.5.5/x", P));
    }

    @Test
    void validatePublicHttpUrl_链路本地地址_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://169.254.1.1/x", P));
    }

    @Test
    void validatePublicHttpUrl_公网IPv4_放行() {
        URI uri = SafeUrlValidator.validatePublicHttpUrl("http://8.8.8.8/x", P);
        assertEquals("8.8.8.8", uri.getHost());
    }

    @Test
    void validatePublicHttpUrl_IPv6回环_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validatePublicHttpUrl("http://[::1]/x", P));
    }

    @Test
    void validatePublicHttpUrl_IPv6公网_放行() {
        URI uri = SafeUrlValidator.validatePublicHttpUrl("http://[2606:4700:4700::1111]/x", P);
        assertEquals("[2606:4700:4700::1111]", uri.getHost());
    }

    // ===== 基础 URL 规范化（用 IP 字面量避免 DNS 依赖，保证离线确定性）=====

    @Test
    void normalizePublicBaseUrl_去尾部斜杠() {
        assertEquals("https://8.8.8.8",
                SafeUrlValidator.normalizePublicBaseUrl("https://8.8.8.8///", P));
    }

    @Test
    void normalizePublicBaseUrl_含查询参数_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.normalizePublicBaseUrl("https://8.8.8.8/x?a=1", P));
    }

    @Test
    void normalizePublicBaseUrl_含片段_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.normalizePublicBaseUrl("https://8.8.8.8/#f", P));
    }

    @Test
    void normalizePublicBaseUrl_空串_抛异常() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.normalizePublicBaseUrl("   ", P));
    }

    // ===== 限长读取 =====

    @Test
    void readLimited_未超限_返回全部() throws IOException {
        byte[] data = SafeUrlValidator.readLimited(
                new ByteArrayInputStream("hello".getBytes()), 10, P);
        assertEquals("hello", new String(data));
    }

    @Test
    void readLimited_超限_抛异常() {
        assertThrows(BusinessException.class, () -> SafeUrlValidator.readLimited(
                new ByteArrayInputStream("hello world".getBytes()), 5, P));
    }
}
