package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.dto.ChatRequest;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiChatService 单元测试：覆盖确定性同步路径与纯逻辑 helper。
 * <p>已覆盖：{@code chatCompletion} 同步非流式（成功-usage / 成功-估算 / 非200 / 按 modelId 委托）、
 * {@code currentSubject} 登录态、{@code resolveConfig} 三分支（自带key / modelId / 默认）、
 * {@code chatCompletionsUrl} 两分支、{@code parseUsage}、{@code estimateTokens}、
 * {@code truncate}、{@code isClientDisconnect}、{@code recordTokenUsageForMessages}。
 * <p>未覆盖（刻意）：doStreamChat* / doStreamChatWithTools / executeToolAndContinue 等异步 SSE 编排，
 * 属非确定性且边际价值低（不定时轮询/长连接已另有集成验证）。
 */
class AiChatServiceTest {

    private AiModelService aiModelService;
    private ObjectMapper objectMapper;
    private SysAiUsageService sysAiUsageService;
    private AiTaskService aiTaskService;
    private AiChatToolService aiChatToolService;
    private ExternalCallLogger externalCallLogger;
    private HttpClient mockHttpClient;
    private com.stellar.ai.protocol.OpenAiHttpChatClient llmClient;
    private AiChatService service;
    private AiUsageRecorder aiUsageRecorder;

    @BeforeEach
    void setUp() throws Exception {
        aiModelService = mock(AiModelService.class);
        objectMapper = new ObjectMapper();
        sysAiUsageService = mock(SysAiUsageService.class);
        aiTaskService = mock(AiTaskService.class);
        aiChatToolService = mock(AiChatToolService.class);
        externalCallLogger = mock(ExternalCallLogger.class);
        aiUsageRecorder = new AiUsageRecorder(sysAiUsageService, aiTaskService);
        llmClient = new com.stellar.ai.protocol.OpenAiHttpChatClient(objectMapper);
        service = new AiChatService(aiModelService, objectMapper, aiUsageRecorder,
                aiChatToolService, externalCallLogger, llmClient);
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(llmClient, "httpClient", mockHttpClient);
    }

    // ===== 同步非流式 chatCompletion =====

    @Test
    void chatCompletion_成功_返回内容并记usage() throws Exception {
        AiResolvedConfig cfg = new AiResolvedConfig(null, 9L, "https://api.openai.com", "k", "gpt-4", "TEXT");
        when(aiModelService.resolveDefaultConfig("TEXT")).thenReturn(cfg);
        stubSend(200, "{\"choices\":[{\"message\":{\"content\":\"你好\"}}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}");

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(() -> StpUtil.getLoginIdAsString()).thenReturn("42");

            String result = service.chatCompletion("hello");

            assertEquals("你好", result);
            ArgumentCaptor<String> sourceCap = ArgumentCaptor.forClass(String.class);
            verify(sysAiUsageService).record(eq("account"), eq("42"), eq(9L), eq("gpt-4"), eq("TEXT"),
                    anyInt(), anyInt(), anyInt(), sourceCap.capture());
            assertEquals("usage", sourceCap.getValue());
        }
    }

    @Test
    void chatCompletion_无usage_走估算() throws Exception {
        AiResolvedConfig cfg = new AiResolvedConfig(null, 9L, "https://api.openai.com", "k", "gpt-4", "TEXT");
        when(aiModelService.resolveDefaultConfig("TEXT")).thenReturn(cfg);
        // 无 usage 节点 → 走 estimateTokens 兜底
        stubSend(200, "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}");

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(() -> StpUtil.getLoginIdAsString()).thenReturn("42");

            String result = service.chatCompletion("hello world");

            assertEquals("你好", result);
            ArgumentCaptor<Integer> totalCap = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> sourceCap = ArgumentCaptor.forClass(String.class);
            verify(sysAiUsageService).record(eq("account"), eq("42"), eq(9L), eq("gpt-4"), eq("TEXT"),
                    anyInt(), anyInt(), totalCap.capture(), sourceCap.capture());
            assertEquals("estimate", sourceCap.getValue());
            // prompt "hello world"=10 拉丁→ceil(10/4)=3；result "你好"=2 CJK→ceil(2/1.5)=2；total=5
            assertEquals(5, totalCap.getValue().intValue());
        }
    }

    @Test
    void chatCompletion_非200_抛BusinessException() throws Exception {
        AiResolvedConfig cfg = new AiResolvedConfig(null, 9L, "https://api.openai.com", "k", "gpt-4", "TEXT");
        when(aiModelService.resolveDefaultConfig("TEXT")).thenReturn(cfg);
        stubSend(500, "{\"error\":\"boom\"}");

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(() -> StpUtil.getLoginIdAsString()).thenReturn("42");

            assertThrows(BusinessException.class, () -> service.chatCompletion("hi"));
            // 失败路径记 failure 日志（mock 无副作用），不落历史（aiTaskService 未调用）
            verify(aiTaskService, never()).record(any());
        }
    }

    @Test
    void chatCompletion_带modelId_委托resolveConfig() throws Exception {
        AiResolvedConfig cfg = new AiResolvedConfig(7L, 2L, "https://p.com", "k", "gpt-4o", "TEXT");
        when(aiModelService.resolveConfig(7L)).thenReturn(cfg);
        stubSend(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(() -> StpUtil.getLoginIdAsString()).thenReturn("1");

            String result = service.chatCompletion(7L, "prompt");

            assertEquals("ok", result);
            verify(aiModelService).resolveConfig(7L);
        }
    }

    // ===== currentSubject =====

    @Test
    void currentSubject_登录态_返回account主体() throws Exception {
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(() -> StpUtil.getLoginIdAsString()).thenReturn("42");

            Object subject = invoke("currentSubject", new Class[]{});
            Method typeM = subject.getClass().getMethod("type");
            Method idM = subject.getClass().getMethod("id");
            assertEquals("account", typeM.invoke(subject));
            assertEquals("42", idM.invoke(subject));
        }
    }

    // ===== resolveConfig 三分支 =====

    @Test
    void resolveConfig_自带key齐全_返回临时配置() throws Exception {
        InetAddress publicIp = InetAddress.getByAddress("example.com", new byte[]{8, 8, 8, 8});
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getAllByName("example.com"))
                    .thenReturn(new InetAddress[]{publicIp});

            ChatRequest req = new ChatRequest();
            req.setEndpoint("https://example.com/");
            req.setApiKey("k");
            req.setModel("my-model");

            AiResolvedConfig cfg = (AiResolvedConfig) invoke("resolveConfig",
                    new Class[]{ChatRequest.class}, req);
            assertNull(cfg.providerId());
            assertEquals("TEXT", cfg.modelType());
            assertEquals("my-model", cfg.model());
            assertEquals("https://example.com", cfg.endpoint());
        }
    }

    @Test
    void resolveConfig_仅modelId_委托resolveConfig() throws Exception {
        AiResolvedConfig expected = new AiResolvedConfig(5L, 2L, "https://p.com", "k", "m", "TEXT");
        when(aiModelService.resolveConfig(5L)).thenReturn(expected);

        ChatRequest req = new ChatRequest();
        req.setModelId(5L);

        assertSame(expected, invoke("resolveConfig", new Class[]{ChatRequest.class}, req));
        verify(aiModelService).resolveConfig(5L);
    }

    @Test
    void resolveConfig_均无_委托默认TEXT() throws Exception {
        AiResolvedConfig def = new AiResolvedConfig(null, 9L, "https://d.com", "k", "gpt", "TEXT");
        when(aiModelService.resolveDefaultConfig("TEXT")).thenReturn(def);

        ChatRequest req = new ChatRequest();

        assertSame(def, invoke("resolveConfig", new Class[]{ChatRequest.class}, req));
        verify(aiModelService).resolveDefaultConfig("TEXT");
    }

    // ===== chatCompletionsUrl 两分支（OpenAiHttpChatClient）=====

    @Test
    void chatCompletionsUrl_有providerId_去尾斜杠() {
        AiResolvedConfig cfg = new AiResolvedConfig(1L, 1L, "https://api.openai.com/", "k", "gpt", "TEXT");
        assertEquals("https://api.openai.com/v1/chat/completions", llmClient.chatCompletionsUrl(cfg));
    }

    @Test
    void chatCompletionsUrl_无providerId_自定义endpoint() throws Exception {
        InetAddress publicIp = InetAddress.getByAddress("example.com", new byte[]{8, 8, 8, 8});
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getAllByName("example.com"))
                    .thenReturn(new InetAddress[]{publicIp});

            AiResolvedConfig cfg = new AiResolvedConfig(null, null, "https://example.com/", "k", "m", "TEXT");
            assertEquals("https://example.com/v1/chat/completions", llmClient.chatCompletionsUrl(cfg));
        }
    }

    // ===== parseUsage（OpenAiHttpChatClient）=====

    @Test
    void parseUsage_空节点返回null() throws Exception {
        assertNull(llmClient.parseUsage(null));
        JsonNode missing = objectMapper.readTree("{}").path("usage");
        assertNull(llmClient.parseUsage(missing));
    }

    @Test
    void parseUsage_有total_tokens返回数组() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}").path("usage");
        assertArrayEquals(new int[]{1, 2, 3}, llmClient.parseUsage(node));
    }

    // ===== estimateTokens（AiUsageRecorder）=====

    @Test
    void estimateTokens_空文本返回0() {
        assertEquals(0, aiUsageRecorder.estimateTokens(null));
        assertEquals(0, aiUsageRecorder.estimateTokens(""));
    }

    @Test
    void estimateTokens_纯CJK按15字符每token() {
        // 4 CJK → ceil(4/1.5 + 0)=3
        assertEquals(3, aiUsageRecorder.estimateTokens("你好世界"));
    }

    @Test
    void estimateTokens_混合中英() {
        // "hi你好": cjk=2→2/1.5=1.333, latin=2→2/4=0.5, 先求和再 ceil(1.833)=2
        assertEquals(2, aiUsageRecorder.estimateTokens("hi你好"));
    }

    // ===== truncate (static) =====

    @Test
    void truncate_null返回空串() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("truncate", String.class, int.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(null, (String) null, 200));
    }

    @Test
    void truncate_超长截断加省略号() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("truncate", String.class, int.class);
        m.setAccessible(true);
        String longStr = "x".repeat(250);
        Object r = m.invoke(null, longStr, 200);
        assertTrue(((String) r).endsWith("..."));
        assertEquals(203, ((String) r).length());
    }

    // ===== isClientDisconnect =====

    @Test
    void isClientDisconnect_各类断开识别() throws Exception {
        assertFalse((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class}, (Object) null));
        assertTrue((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class}, new IOException("Broken pipe")));
        assertTrue((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class}, new IOException("Connection reset")));
        assertTrue((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class}, new IOException("Async request not usable")));
        assertTrue((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class},
                new IOException("Responsebodyemitter has already completed")));
        // 嵌套 cause
        assertTrue((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class},
                new RuntimeException(new IOException("Connection reset"))));
        assertFalse((Boolean) invoke("isClientDisconnect", new Class[]{Throwable.class}, new RuntimeException("ok")));
    }

    // ===== recordTokenUsageForMessages（AiUsageRecorder）=====

    @Test
    void recordTokenUsageForMessages_委托记录usage() {
        AiResolvedConfig cfg = new AiResolvedConfig(1L, 3L, "e", "k", "m", "TEXT");
        List<Map<String, Object>> msgs = List.of(Map.of("role", "user", "content", "你好abc"));

        aiUsageRecorder.recordTokenUsageForMessages(cfg, "m", msgs, "结果", true,
                new int[]{1, 2, 3}, "account", "1");

        verify(sysAiUsageService).record(eq("account"), eq("1"), eq(3L), eq("m"), eq("TEXT"),
                anyInt(), anyInt(), anyInt(), eq("usage"));
    }

    // ===== helpers =====

    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        Method m = AiChatService.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private void stubSend(int status, String body) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        doReturn(resp).when(mockHttpClient).send(any(), any());
    }
}
