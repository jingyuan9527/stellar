package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.AiVideoCreateDTO;
import com.stellar.ai.dto.AiVideoHistoryQueryDTO;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.ai.vo.AiVideoHistoryVO;
import com.stellar.common.BusinessException;
import com.stellar.system.entity.SysFile;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.system.service.FileService;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiVideoService} 单测：反射注入 final HttpClient + MockedStatic StpUtil，覆盖
 * 模型类型拦截、创建/查询成功与各类异常（HTTP 错误经 friendlyVideoError 转译、缺 video_id）、
 * 历史分页映射、删除归属校验，以及 completed 复用本地 file / 触发下载落库两条分支。
 */
@ExtendWith(MockitoExtension.class)
class AiVideoServiceTest {

    @Mock
    AiModelService aiModelService;
    @Mock
    com.stellar.system.service.FileService fileService;
    @Mock
    SysAiUsageService sysAiUsageService;
    @Mock
    AiTaskMapper aiTaskMapper;
    @Mock
    ExternalCallLogger externalCallLogger;

    HttpClient mockHttpClient;
    AiVideoService service;

    @BeforeEach
    void setup() {
        service = new AiVideoService(aiModelService, fileService, sysAiUsageService, aiTaskMapper,
                new com.fasterxml.jackson.databind.ObjectMapper(), externalCallLogger);
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(service, "httpClient", mockHttpClient);
    }

    private AiResolvedConfig videoConfig() {
        return new AiResolvedConfig(1L, 2L, "http://video.test/", "key", "vid-model", "VIDEO");
    }

    private AiResolvedConfig textConfig() {
        return new AiResolvedConfig(1L, 2L, "http://video.test/", "key", "txt-model", "TEXT");
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

    private AiVideoCreateDTO createDto() {
        AiVideoCreateDTO d = new AiVideoCreateDTO();
        d.setModelId(1L);
        d.setPrompt("a cat");
        return d;
    }

    @Test
    void createTask_非VIDEO模型_抛() {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(textConfig());
        assertThrows(BusinessException.class, () -> service.createTask(createDto()));
    }

    @Test
    void createTask_http400_转译错误信息() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(400, "{\"error\":{\"message\":\"bad req\"}}"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            BusinessException ex = assertThrows(BusinessException.class, () -> service.createTask(createDto()));
            assertTrue(ex.getMessage().contains("bad req"), ex.getMessage());
        }
    }

    @Test
    void createTask_http500_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(500, "err"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.createTask(createDto()));
        }
    }

    @Test
    void createTask_缺videoId_抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(200, "{\"status\":\"queued\"}"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.createTask(createDto()));
        }
    }

    @Test
    void createTask_成功_记录用量并落痕() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(200, "{\"video_id\":\"vid1\",\"status\":\"queued\"}"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("1");
            var vo = service.createTask(createDto());
            assertEquals("vid1", vo.getVideoId());
            verify(sysAiUsageService).record(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any());
            verify(aiTaskMapper).insert(any(AiTask.class));
        }
    }

    @Test
    void getTask_非VIDEO模型_抛() {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(textConfig());
        assertThrows(BusinessException.class, () -> service.getTask(1L, "vid1"));
    }

    @Test
    void getTask_队列中_返回状态() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(200, "{\"status\":\"queued\"}"));
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(null);
        var vo = service.getTask(1L, "vid1");
        assertEquals("queued", vo.getStatus());
    }

    @Test
    void getTask_完成_复用本地文件() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(200, "{\"status\":\"completed\"}"));
        AiTask local = new AiTask();
        local.setFileId(99L);
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(local);
        var vo = service.getTask(1L, "vid1");
        assertEquals("/file/99", vo.getVideoUrl());
        verify(fileService, never()).create((SysFile) any());
    }

    @Test
    void getTask_失败_不抛() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        stubSend(jsonResponse(200, "{\"status\":\"failed\"}"));
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(null);
        var vo = service.getTask(1L, "vid1");
        assertEquals("failed", vo.getStatus());
    }

    @Test
    void getTask_完成_下载并落库() throws Exception {
        when(aiModelService.resolveConfig(anyLong())).thenReturn(videoConfig());
        HttpResponse<String> query = jsonResponse(200,
                "{\"status\":\"completed\",\"metadata\":{\"url\":\"http://download.example.com/x.mp4\"}}");
        HttpResponse<InputStream> dl = mock(HttpResponse.class);
        when(dl.statusCode()).thenReturn(200);
        when(dl.body()).thenReturn(new ByteArrayInputStream("vidbytes".getBytes()));
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.firstValueAsLong("Content-Length")).thenReturn(OptionalLong.of(9L));
        when(dl.headers()).thenReturn(headers);
        doAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("agnesapi") ? query : dl;
        }).when(mockHttpClient).send(any(), any());
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(null);
        // SafeUrlValidator 会做真实 DNS 解析，这里桩掉返回公网 IP 使校验通过
        java.net.InetAddress publicIp = java.net.InetAddress.getByAddress("h", new byte[]{8, 8, 8, 8});
        try (MockedStatic<java.net.InetAddress> inet = mockStatic(java.net.InetAddress.class)) {
            inet.when(() -> java.net.InetAddress.getAllByName(anyString()))
                    .thenReturn(new java.net.InetAddress[]{publicIp});

            var vo = service.getTask(1L, "vid1");
            verify(fileService).create((SysFile) any());
            assertTrue(vo.getVideoUrl().startsWith("/file/"));
        }
    }

    @Test
    void pageHistory_映射extra字段() {
        AiTask t = new AiTask();
        t.setExtra("{\"model_id\":1,\"ratio\":\"16:9\",\"duration\":5}");
        t.setFileId(3L);
        Page<AiTask> page = new Page<>();
        page.setRecords(List.of(t));
        page.setTotal(1);
        when(aiTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<AiVideoHistoryVO> vo = service.pageHistory(new AiVideoHistoryQueryDTO(), "account", "1");
        assertEquals(1, vo.getRecords().size());
        assertEquals("16:9", vo.getRecords().get(0).getRatio());
        assertEquals(5, vo.getRecords().get(0).getDuration());
        assertEquals("/file/3", vo.getRecords().get(0).getUrl());
    }

    @Test
    void deleteTask_不存在_抛() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteTask(1L, "account", "1"));
    }

    @Test
    void deleteTask_主体不符_抛() {
        AiTask t = new AiTask();
        t.setSubjectType("ip");
        t.setSubjectId("9.9.9.9");
        when(aiTaskMapper.selectById(1L)).thenReturn(t);
        assertThrows(BusinessException.class, () -> service.deleteTask(1L, "account", "1"));
    }

    @Test
    void deleteTask_正常_删文件与任务() {
        AiTask t = new AiTask();
        t.setSubjectType("account");
        t.setSubjectId("1");
        t.setFileId(9L);
        when(aiTaskMapper.selectById(1L)).thenReturn(t);
        service.deleteTask(1L, "account", "1");
        verify(fileService).deleteById(9L);
        verify(aiTaskMapper).deleteById(1L);
    }

    @Test
    void assertVideoOwner_无本地任务_抛() {
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.assertVideoOwner("vid1", "account", "1"));
    }

    @Test
    void assertVideoOwner_主体不符_抛() {
        AiTask t = new AiTask();
        t.setSubjectType("account");
        t.setSubjectId("2");
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(t);
        assertThrows(BusinessException.class,
                () -> service.assertVideoOwner("vid1", "account", "1"));
    }

    @Test
    void assertVideoOwner_归属匹配_通过() {
        AiTask t = new AiTask();
        t.setSubjectType("account");
        t.setSubjectId("1");
        when(aiTaskMapper.selectVideoTaskByVideoId("vid1")).thenReturn(t);
        service.assertVideoOwner("vid1", "account", "1");
        verify(aiTaskMapper).selectVideoTaskByVideoId("vid1");
    }
}
