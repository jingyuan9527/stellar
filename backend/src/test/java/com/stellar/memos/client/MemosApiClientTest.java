package com.stellar.memos.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link MemosApiClient} 单测：注入 mock HttpClient，覆盖
 * ListMemos 分页拉取/Connect 失败走 REST 兜底/更新时间解析，UpdateMemo 双通道。
 */
@ExtendWith(MockitoExtension.class)
class MemosApiClientTest {

    @Mock
    private HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MemosApiClient client;

    @BeforeEach
    void setUp() {
        client = new MemosApiClient(objectMapper, httpClient);
    }

    private HttpResponse<String> resp(int status, String body) {
        // 直接 mock 接口，避免泛型推断问题
        @SuppressWarnings("unchecked")
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    // ===== ListMemos =====

    @Test
    void connectBody_值统一带JSON引号_空值与等号字符不丢失() {
        assertEquals("{\"pageSize\":\"100\",\"pageToken\":\"\"}",
                MemosApiClient.connectBody("pageSize", "100", "pageToken", ""));
        assertEquals("{\"pageSize\":\"100\",\"pageToken\":\"CGQQrAI=\"}",
                MemosApiClient.connectBody("pageSize", "100", "pageToken", "CGQQrAI="));
        assertEquals("{\"a\":\"\\\"quoted\\\"\",\"b\":\"back\\\\slash\"}",
                MemosApiClient.connectBody("a", "\"quoted\"", "b", "back\\slash"));
    }

    @Test
    void listAllMemos_分页遍历_解析uid内容时间标签() throws Exception {
        String page1 = "{\"memos\":[{\"name\":\"memos/1\",\"uid\":\"uid1\",\"content\":\"a\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-02T00:00:00Z\","
                + "\"tags\":[\"x\",\"y\"]}],\"nextPageToken\":\"tok2\"}";
        String page2 = "{\"memos\":[{\"name\":\"memos/2\",\"uid\":\"uid2\",\"content\":\"b\","
                + "\"createTime\":\"2025-02-01T00:00:00Z\",\"updateTime\":\"2025-02-01T00:00:00Z\","
                + "\"tags\":[]}],\"nextPageToken\":\"\"}";
        HttpResponse<String> r1 = resp(200, page1);
        HttpResponse<String> r2 = resp(200, page2);
        doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

        List<MemosApiClient.MemosRemoteMemo> list = client.listAllMemos("https://memo.booksy.cf", "tok");

        assertEquals(2, list.size());
        assertEquals("uid1", list.get(0).uid());
        assertEquals("a", list.get(0).content());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), list.get(0).createTime());
        assertEquals(List.of("x", "y"), list.get(0).tags());
        assertEquals("uid2", list.get(1).uid());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void listAllMemos_Connect失败_走REST兜底() throws Exception {
        // 第一次（Connect）抛异常，第二次（REST）返回 200
        HttpResponse<String> rest = resp(200, "{\"memos\":[{\"uid\":\"u1\",\"content\":\"c\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-01T00:00:00Z\","
                + "\"tags\":[]}],\"nextPageToken\":\"\"}");
        doThrow(new RuntimeException("connect down"))
                .doReturn(rest)
                .when(httpClient).send(any(), any());

        List<MemosApiClient.MemosRemoteMemo> list = client.listAllMemos("https://memo.booksy.cf", "tok");

        assertEquals(1, list.size());
        assertEquals("u1", list.get(0).uid());
    }

    @Test
    void listAllMemos_v030无uid字段_从name提取uid() throws Exception {
        // v0.30+ Memo 消息无 uid 字段，uid 嵌在 name（memos/{uid}）
        HttpResponse<String> r = resp(200, "{\"memos\":[{\"name\":\"memos/abc123\",\"content\":\"c\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-01T00:00:00Z\","
                + "\"tags\":[\"t1\"]}],\"nextPageToken\":\"\"}");
        doReturn(r).when(httpClient).send(any(), any());

        List<MemosApiClient.MemosRemoteMemo> list = client.listAllMemos("https://memo.booksy.cf", "tok");

        assertEquals(1, list.size());
        assertEquals("abc123", list.get(0).uid());
        assertEquals(List.of("t1"), list.get(0).tags());
    }

    @Test
    void listAllMemos_uid缺失_跳过() throws Exception {
        doReturn(resp(200, "{\"memos\":[{\"content\":\"no uid\"},{\"uid\":\"ok\",\"content\":\"c\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-01T00:00:00Z\","
                + "\"tags\":[]}],\"nextPageToken\":\"\"}"))
                .when(httpClient).send(any(), any());

        List<MemosApiClient.MemosRemoteMemo> list = client.listAllMemos("https://memo.booksy.cf", "tok");
        assertEquals(1, list.size());
        assertEquals("ok", list.get(0).uid());
    }

    @Test
    void listAllMemos_非200_抛异常() throws Exception {
        doReturn(resp(401, "unauthorized")).when(httpClient).send(any(), any());
        assertThrows(BusinessException.class,
                () -> client.listAllMemos("https://memo.booksy.cf", "tok"));
    }

    // ===== parseMemo（webhook payload 解析） =====

    @Test
    void parseMemo_完整memo_解析uid内容时间标签() throws Exception {
        var memo = objectMapper.readTree("{\"name\":\"memos/w1\",\"uid\":\"w1\",\"content\":\"hello\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-02T00:00:00Z\","
                + "\"property\":{\"tags\":[\"a\",\"b\"]}}");

        MemosApiClient.MemosRemoteMemo rm = client.parseMemo(memo);

        assertEquals("w1", rm.uid());
        assertEquals("hello", rm.content());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), rm.createTime());
        assertEquals(LocalDateTime.of(2025, 1, 2, 0, 0), rm.updateTime());
        assertEquals(List.of("a", "b"), rm.tags());
    }

    @Test
    void parseMemo_缺uid_返回null() throws Exception {
        var noUid = objectMapper.readTree("{\"content\":\"x\"}");
        assertNull(client.parseMemo(noUid));
        assertNull(client.parseMemo(null));
    }

    // ===== UpdateMemo =====

    @Test
    void updateContent_Connect成功_不发REST兜底() throws Exception {
        doReturn(resp(200, "{\"memo\":{}}")).when(httpClient).send(any(), any());
        client.updateContent("https://memo.booksy.cf", "tok", "uid1", "content #tag");
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void updateContent_Connect失败_REST兜底成功() throws Exception {
        HttpResponse<String> rest = resp(200, "{\"memo\":{}}");
        doThrow(new RuntimeException("connect down"))
                .doReturn(rest)
                .when(httpClient).send(any(), any());
        client.updateContent("https://memo.booksy.cf", "tok", "uid1", "content #tag");
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void updateContent_双通道都失败_抛异常() throws Exception {
        HttpResponse<String> err = resp(500, "boom");
        doThrow(new RuntimeException("connect down"))
                .doReturn(err)
                .when(httpClient).send(any(), any());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.updateContent("https://memo.booksy.cf", "tok", "uid1", "c"));
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }
}
