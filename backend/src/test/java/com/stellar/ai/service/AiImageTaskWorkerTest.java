package com.stellar.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.system.entity.SysFile;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiImageTaskWorker} 单测：注入 mock HttpClient 隔离网络，
 * 覆盖 generateImageBytes 的 b64/URL/错误状态/空数据分支，以及 doGenerateAsync 的
 * 任务不存在/成功落库/失败通知分支。
 */
class AiImageTaskWorkerTest {

    private AiModelService aiModelService;
    private AiTaskMapper aiTaskMapper;
    private SysFileMapper fileMapper;
    private SysAiUsageService sysAiUsageService;
    private AiNotifyPublisher publisher;
    private ExternalCallLogger externalCallLogger;
    private PlatformTransactionManager transactionManager;
    private HttpClient mockHttpClient;
    private AiImageTaskWorker worker;

    @BeforeEach
    void setUp() {
        aiModelService = mock(AiModelService.class);
        aiTaskMapper = mock(AiTaskMapper.class);
        fileMapper = mock(SysFileMapper.class);
        sysAiUsageService = mock(SysAiUsageService.class);
        publisher = mock(AiNotifyPublisher.class);
        externalCallLogger = mock(ExternalCallLogger.class);
        transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        // 事务管理器在事务路径测试中用，generateImageBytes/任务不存在等用例不触达，故 lenient
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        worker = new AiImageTaskWorker(aiModelService, aiTaskMapper, fileMapper,
                sysAiUsageService, new ObjectMapper(), publisher, externalCallLogger, transactionManager);
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(worker, "httpClient", mockHttpClient);
    }

    private AiResolvedConfig imageCfg() {
        return new AiResolvedConfig(1L, 1L, "https://ep.example.com", "key", "m", "IMAGE");
    }

    private AiTask imageTask(Long id, String subjectType, String subjectId, String extra) {
        AiTask t = new AiTask();
        t.setId(id);
        t.setTaskType("image");
        t.setSubjectType(subjectType);
        t.setSubjectId(subjectId);
        t.setPrompt("a cute cat");
        t.setStatus("processing");
        t.setExtra(extra);
        t.setRequestTime(LocalDateTime.now());
        return t;
    }

    private void stubImageSend(int status, String body) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        doReturn(resp).when(mockHttpClient).send(any(), any());
    }

    // ===== generateImageBytes =====

    @Test
    void generateImageBytes_b64成功_返回解码字节() throws Exception {
        String b64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        stubImageSend(200, "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");

        byte[] bytes = worker.generateImageBytes(imageCfg(), "a cute cat", null, null);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
        verify(externalCallLogger).success(eq("AI图片"), anyString(), anyString(), anyLong());
    }

    @Test
    void generateImageBytes_状态码429_抛友好信息() throws Exception {
        stubImageSend(429, "{\"error\":{\"message\":\"rate limited\"}}");

        BusinessException e = assertThrows(BusinessException.class,
                () -> worker.generateImageBytes(imageCfg(), "p", null, null));

        assertEquals("请求过于频繁，请稍后重试", e.getMessage());
        verify(externalCallLogger).failure(eq("AI图片"), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void generateImageBytes_状态码400_抛参数错误() throws Exception {
        stubImageSend(400, "{\"error\":{\"message\":\"bad prompt\"}}");

        BusinessException e = assertThrows(BusinessException.class,
                () -> worker.generateImageBytes(imageCfg(), "p", null, null));

        assertTrue(e.getMessage().contains("bad prompt"));
    }

    @Test
    void generateImageBytes_数据为空_抛() throws Exception {
        stubImageSend(200, "{\"data\":[]}");

        BusinessException e = assertThrows(BusinessException.class,
                () -> worker.generateImageBytes(imageCfg(), "p", null, null));

        assertTrue(e.getMessage().contains("返回数据为空"));
    }

    @Test
    void generateImageBytes_无b64无url_抛() throws Exception {
        stubImageSend(200, "{\"data\":[{\"foo\":\"bar\"}]}");

        BusinessException e = assertThrows(BusinessException.class,
                () -> worker.generateImageBytes(imageCfg(), "p", null, null));

        assertTrue(e.getMessage().contains("未返回图片数据"));
    }

    @Test
    void generateImageBytes_url分支_下载成功() throws Exception {
        InetAddress publicIp = InetAddress.getByAddress("cdn.example.com", new byte[]{8, 8, 8, 8});
        HttpResponse<String> genResp = mock(HttpResponse.class);
        when(genResp.statusCode()).thenReturn(200);
        when(genResp.body()).thenReturn("{\"data\":[{\"url\":\"https://cdn.example.com/img.png\"}]}");
        HttpResponse<Object> downloadResp = mock(HttpResponse.class);
        when(downloadResp.statusCode()).thenReturn(200);
        when(downloadResp.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("Content-Length", java.util.List.of("5")), (k, v) -> true));
        when(downloadResp.body()).thenReturn(new java.io.ByteArrayInputStream("hello".getBytes()));
        // 第一次 send=生成接口，第二次 send=下载图片
        doReturn(genResp).doReturn(downloadResp).when(mockHttpClient).send(any(), any());

        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getAllByName("cdn.example.com"))
                    .thenReturn(new InetAddress[]{publicIp});
            byte[] bytes = worker.generateImageBytes(imageCfg(), "p", null, null);
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
        }
    }

    @Test
    void generateImageBytes_请求异常_重抛() throws Exception {
        when(mockHttpClient.send(any(), any())).thenThrow(new java.io.IOException("conn reset"));

        assertThrows(java.io.IOException.class,
                () -> worker.generateImageBytes(imageCfg(), "p", null, null));
        verify(externalCallLogger).failure(eq("AI图片"), anyString(), anyString(), anyString(), anyLong());
    }

    // ===== doGenerateAsync =====

    @Test
    void doGenerateAsync_任务不存在_直接返回() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);

        worker.doGenerateAsync(1L);

        verify(aiTaskMapper).selectById(1L);
        verify(publisher, never()).publish(any());
    }

    @Test
    void doGenerateAsync_成功_落库记usage并通知() throws Exception {
        AiTask task = imageTask(1L, "account", "u1", "{\"model_id\":1,\"size\":\"1K\",\"ratio\":\"1:1\"}");
        when(aiTaskMapper.selectById(1L)).thenReturn(task);
        when(aiModelService.resolveConfig(1L)).thenReturn(imageCfg());
        String b64 = Base64.getEncoder().encodeToString("img".getBytes(StandardCharsets.UTF_8));
        stubImageSend(200, "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");
        SysFile saved = new SysFile();
        saved.setId(99L);
        when(fileMapper.insert(any(SysFile.class))).thenAnswer(inv -> {
            SysFile f = inv.getArgument(0);
            f.setId(99L);
            return 1;
        });

        worker.doGenerateAsync(1L);

        ArgumentCaptor<SysFile> fileCap = ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(fileCap.capture());
        assertEquals("png", fileCap.getValue().getExt());
        assertArrayEquals("img".getBytes(StandardCharsets.UTF_8), fileCap.getValue().getData());

        assertEquals("completed", task.getStatus());
        assertEquals(99L, task.getFileId());
        verify(aiTaskMapper).updateById(task);
        verify(sysAiUsageService).record(eq("account"), eq("u1"), eq(1L), eq("m"), eq("IMAGE"),
                anyInt(), anyInt(), anyInt(), eq("estimate"));
        ArgumentCaptor<AiNotifyMessage> notifyCap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(notifyCap.capture());
        assertEquals("account:u1", notifyCap.getValue().subject());
        assertEquals("completed", notifyCap.getValue().status());
    }

    @Test
    void doGenerateAsync_生成失败_标记失败并通知() throws Exception {
        AiTask task = imageTask(2L, "ip", "1.2.3.4", null);
        when(aiTaskMapper.selectById(2L)).thenReturn(task);
        when(aiModelService.resolveConfig(any())).thenReturn(imageCfg());
        stubImageSend(429, "{}");

        worker.doGenerateAsync(2L);

        assertEquals("failed", task.getStatus());
        assertTrue(task.getErrorMsg().contains("请求过于频繁"));
        verify(aiTaskMapper).updateById(task);
        ArgumentCaptor<AiNotifyMessage> notifyCap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(notifyCap.capture());
        assertEquals("failed", notifyCap.getValue().status());
        assertEquals("ip:1.2.3.4", notifyCap.getValue().subject());
    }

    @Test
    void doGenerateAsync_extra非法_忽略仍生成() throws Exception {
        AiTask task = imageTask(3L, "account", "u1", "not-json");
        when(aiTaskMapper.selectById(3L)).thenReturn(task);
        when(aiModelService.resolveConfig(any())).thenReturn(imageCfg());
        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        stubImageSend(200, "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");
        SysFile saved = new SysFile();
        saved.setId(5L);
        when(fileMapper.insert(any(SysFile.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysFile.class).setId(5L);
            return 1;
        });

        worker.doGenerateAsync(3L);

        assertEquals("completed", task.getStatus());
    }
}
