package com.stellar.ai.service;

import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiEmbeddingService} 单测：通过 {@link ReflectUtil} 把字段初始化的 final HttpClient 替换为 mock，
 * 覆盖纯逻辑 toVectorLiteral、空输入早退，以及 HTTP 200 解析 / 非 200 / 缺 data 字段分支。
 */
@ExtendWith(MockitoExtension.class)
class AiEmbeddingServiceTest {

    @Mock
    AiModelService aiModelService;
    @Mock
    ExternalCallLogger externalCallLogger;

    HttpClient mockHttpClient;
    AiEmbeddingService service;

    @BeforeEach
    void setup() {
        service = new AiEmbeddingService(aiModelService, new com.fasterxml.jackson.databind.ObjectMapper(), externalCallLogger);
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(service, "httpClient", mockHttpClient);
    }

    private AiResolvedConfig embeddingConfig() {
        return new AiResolvedConfig(1L, 2L, "http://embed.test/", "key", "emb-model", "EMBEDDING");
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

    @Test
    void toVectorLiteral_正常拼接() {
        assertEquals("[1.0,2.0,3.0]", service.toVectorLiteral(new float[]{1.0f, 2.0f, 3.0f}));
    }

    @Test
    void embedBatch_空输入_早退不请求() throws Exception {
        List<float[]> r = service.embedBatch(List.of(), 1L);
        assertTrue(r.isEmpty());
        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    void embedBatch_200_按index解析向量() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(embeddingConfig());
        stubSend(jsonResponse(200, "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}"));
        List<float[]> r = service.embedBatch(List.of("hello"), 1L);
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).length);
    }

    @Test
    void embed_单条委托embedBatch() throws Exception {
        when(aiModelService.resolveDefaultConfig("EMBEDDING")).thenReturn(embeddingConfig());
        stubSend(jsonResponse(200, "{\"data\":[{\"index\":0,\"embedding\":[0.5]}]}"));
        float[] v = service.embed("hi", null);
        assertEquals(1, v.length);
    }

    @Test
    void embedBatch_非200_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(embeddingConfig());
        stubSend(jsonResponse(500, "boom"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.embedBatch(List.of("x"), 1L));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void embedBatch_缺data字段_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(embeddingConfig());
        stubSend(jsonResponse(200, "{\"foo\":1}"));
        assertThrows(BusinessException.class, () -> service.embedBatch(List.of("x"), 1L));
    }
}
