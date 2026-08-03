package com.stellar.tts.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.ai.service.AiModelService;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiTtsService} 单测：注入 mock HttpClient + MockedStatic StpUtil，覆盖入参校验、
 * 非 AUDIO 模型拦截，以及 HTTP 200 解析（usage 精确 / estimate 兜底）、非 200、缺音频数据分支。
 */
@ExtendWith(MockitoExtension.class)
class AiTtsServiceTest {

    @Mock
    AiModelService aiModelService;
    @Mock
    SysAiUsageService sysAiUsageService;
    @Mock
    ExternalCallLogger externalCallLogger;

    HttpClient mockHttpClient;
    AiTtsService service;

    @BeforeEach
    void setup() {
        service = new AiTtsService(aiModelService, new com.fasterxml.jackson.databind.ObjectMapper(),
                sysAiUsageService, externalCallLogger);
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(service, "httpClient", mockHttpClient);
    }

    private AiResolvedConfig audioConfig() {
        return new AiResolvedConfig(1L, 2L, "http://tts.test/", "key", "tts-model", "AUDIO");
    }

    private AiResolvedConfig textConfig() {
        return new AiResolvedConfig(1L, 2L, "http://tts.test/", "key", "txt-model", "TEXT");
    }

    private HttpResponse<String> jsonResponse(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    private void stubSend(HttpResponse<String> resp) throws Exception {
        doReturn(resp).when(mockHttpClient).send(any(), any());
    }

    private String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void synthesize_文本为空_抛() {
        assertThrows(BusinessException.class, () -> service.synthesize(1L, "  ", "冰糖", null));
    }

    @Test
    void synthesize_音色为空_抛() {
        assertThrows(BusinessException.class, () -> service.synthesize(1L, "你好", null, null));
    }

    @Test
    void synthesize_非AUDIO模型_抛() {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(textConfig());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.synthesize(1L, "你好", "冰糖", null));
        assertTrue(ex.getMessage().contains("语音合成类型"));
    }

    @Test
    void synthesize_成功_usage精确记录() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(audioConfig());
        String json = "{\"choices\":[{\"message\":{\"audio\":{\"data\":\"" + b64("wavdata") + "\"}}}],"
                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":0,\"total_tokens\":5}}";
        stubSend(jsonResponse(200, json));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            byte[] audio = service.synthesize(1L, "你好", "冰糖", "温柔");
            assertEquals("wavdata".length(), audio.length);
            ArgumentCaptor<String> src = ArgumentCaptor.forClass(String.class);
            verify(sysAiUsageService).record(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), src.capture());
            assertEquals("usage", src.getValue());
        }
    }

    @Test
    void synthesize_成功_无usage_estimate兜底() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(audioConfig());
        String json = "{\"choices\":[{\"message\":{\"audio\":{\"data\":\"" + b64("wav") + "\"}}}]}";
        stubSend(jsonResponse(200, json));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            service.synthesize(1L, "你好", "冰糖", null);
            ArgumentCaptor<String> src = ArgumentCaptor.forClass(String.class);
            verify(sysAiUsageService).record(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), src.capture());
            assertEquals("estimate", src.getValue());
        }
    }

    @Test
    void synthesize_非200_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(audioConfig());
        stubSend(jsonResponse(500, "err"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.synthesize(1L, "你好", "冰糖", null));
        }
    }

    @Test
    void synthesize_缺音频数据_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(audioConfig());
        stubSend(jsonResponse(200, "{\"choices\":[{\"message\":{}}]}"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.synthesize(1L, "你好", "冰糖", null));
        }
    }
}
