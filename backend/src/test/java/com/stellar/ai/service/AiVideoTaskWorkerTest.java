package com.stellar.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.ai.vo.AiVideoStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiVideoTaskWorker} 单测：DB 驱动调度轮询——无可轮询任务直接跳过、
 * completed/failed 发通知、仍在生成/轮询异常推进时间戳不通知、
 * 超过超时阈值或缺 video_id 标记失败且不再查供应商。
 */
class AiVideoTaskWorkerTest {

    private AiVideoService aiVideoService;
    private AiTaskMapper aiTaskMapper;
    private AiNotifyPublisher publisher;
    private AiVideoTaskWorker worker;

    @BeforeEach
    void setUp() {
        aiVideoService = mock(AiVideoService.class);
        aiTaskMapper = mock(AiTaskMapper.class);
        publisher = mock(AiNotifyPublisher.class);
        worker = new AiVideoTaskWorker(aiVideoService, aiTaskMapper, publisher, new ObjectMapper());
    }

    private AiTask pendingVideo(Long id, LocalDateTime created, String extra) {
        AiTask t = new AiTask();
        t.setId(id);
        t.setTaskType("video");
        t.setSubjectType("account");
        t.setSubjectId("u1");
        t.setStatus("generating");
        t.setExtra(extra);
        t.setCreateTime(created);
        t.setRequestTime(created);
        t.setUpdateTime(created);
        return t;
    }

    private AiVideoStatusVO status(String s) {
        AiVideoStatusVO vo = new AiVideoStatusVO();
        vo.setStatus(s);
        return vo;
    }

    @Test
    void pollPendingVideos_无可轮询任务_跳过() {
        when(aiTaskMapper.selectPendingVideoTasks(any())).thenReturn(Collections.emptyList());

        worker.pollPendingVideos();

        verify(aiVideoService, never()).getTask(anyLong(), anyString());
        verify(publisher, never()).publish(any());
        verify(aiTaskMapper, never()).updateById(any(AiTask.class));
    }

    @Test
    void pollPendingVideos_供应商completed_发通知() throws Exception {
        when(aiTaskMapper.selectPendingVideoTasks(any()))
                .thenReturn(List.of(pendingVideo(2L, LocalDateTime.now(), "{\"model_id\":1,\"video_id\":\"vid-2\"}")));
        when(aiVideoService.getTask(1L, "vid-2")).thenReturn(status("completed"));

        worker.pollPendingVideos();

        ArgumentCaptor<AiNotifyMessage> cap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(cap.capture());
        assertEquals("account:u1", cap.getValue().subject());
        assertEquals("video", cap.getValue().type());
        assertEquals("completed", cap.getValue().status());
        assertEquals(2L, cap.getValue().taskId());
        verify(aiTaskMapper, never()).updateById(any(AiTask.class));
    }

    @Test
    void pollPendingVideos_供应商failed_发通知() throws Exception {
        when(aiTaskMapper.selectPendingVideoTasks(any()))
                .thenReturn(List.of(pendingVideo(3L, LocalDateTime.now(), "{\"model_id\":1,\"video_id\":\"vid-3\"}")));
        when(aiVideoService.getTask(1L, "vid-3")).thenReturn(status("failed"));

        worker.pollPendingVideos();

        ArgumentCaptor<AiNotifyMessage> cap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(cap.capture());
        assertEquals("failed", cap.getValue().status());
        assertEquals(3L, cap.getValue().taskId());
        verify(aiTaskMapper, never()).updateById(any(AiTask.class));
    }

    @Test
    void pollPendingVideos_仍在生成_推进时间戳不通知() throws Exception {
        AiTask task = pendingVideo(4L, LocalDateTime.now(), "{\"model_id\":1,\"video_id\":\"vid-4\"}");
        when(aiTaskMapper.selectPendingVideoTasks(any())).thenReturn(List.of(task));
        when(aiVideoService.getTask(1L, "vid-4")).thenReturn(status("generating"));

        worker.pollPendingVideos();

        verify(publisher, never()).publish(any());
        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).updateById(cap.capture());
        assertEquals("generating", cap.getValue().getStatus());
        assertNotNull(cap.getValue().getUpdateTime());
    }

    @Test
    void pollPendingVideos_轮询异常_推进时间戳不通知() throws Exception {
        when(aiTaskMapper.selectPendingVideoTasks(any()))
                .thenReturn(List.of(pendingVideo(5L, LocalDateTime.now(), "{\"model_id\":1,\"video_id\":\"vid-5\"}")));
        when(aiVideoService.getTask(anyLong(), anyString())).thenThrow(new RuntimeException("boom"));

        worker.pollPendingVideos();

        verify(publisher, never()).publish(any());
        verify(aiTaskMapper).updateById(any(AiTask.class));
    }

    void pollPendingVideos_超过超时阈值_标记失败且不查供应商() {
        AiTask task = pendingVideo(6L, LocalDateTime.now().minusMinutes(11),
                "{\"model_id\":1,\"video_id\":\"vid-6\"}");
        when(aiTaskMapper.selectPendingVideoTasks(any())).thenReturn(List.of(task));
        when(aiTaskMapper.selectById(6L)).thenReturn(task);

        worker.pollPendingVideos();

        verify(aiVideoService, never()).getTask(anyLong(), anyString());
        ArgumentCaptor<AiTask> up = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).updateById(up.capture());
        assertEquals("failed", up.getValue().getStatus());
        assertEquals("视频生成超时", up.getValue().getErrorMsg());
        ArgumentCaptor<AiNotifyMessage> pub = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(pub.capture());
        assertEquals("failed", pub.getValue().status());
    }

    @Test
    void pollPendingVideos_缺videoId_标记失败且不查供应商() {
        AiTask task = pendingVideo(7L, LocalDateTime.now(), "{\"model_id\":1}");
        when(aiTaskMapper.selectPendingVideoTasks(any())).thenReturn(List.of(task));
        when(aiTaskMapper.selectById(7L)).thenReturn(task);

        worker.pollPendingVideos();

        verify(aiVideoService, never()).getTask(anyLong(), anyString());
        verify(aiTaskMapper).updateById(any(AiTask.class));
        verify(publisher).publish(any(AiNotifyMessage.class));
    }
}