package com.stellar.ai.service;

import com.stellar.ai.entity.AiTask;
import com.stellar.ai.event.VideoTaskCreatedEvent;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.ai.vo.AiVideoStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiVideoTaskWorker} 单测：轮询任务不存在直接返回、completed/failed 分支发通知。
 * 轮询间隔 5s，每次用例一次供应商状态查询后即返回，避免长等待。
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
        worker = new AiVideoTaskWorker(aiVideoService, aiTaskMapper, publisher);
    }

    private AiTask videoTask(Long id, String subjectType, String subjectId) {
        AiTask t = new AiTask();
        t.setId(id);
        t.setTaskType("video");
        t.setSubjectType(subjectType);
        t.setSubjectId(subjectId);
        t.setRequestTime(LocalDateTime.now());
        return t;
    }

    @Test
    void onVideoTaskCreated_任务不存在_直接返回() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);

        worker.onVideoTaskCreated(new VideoTaskCreatedEvent(1L, 1L, "vid-1"));

        verify(aiTaskMapper).selectById(1L);
        verify(publisher, never()).publish(any());
    }

    @Test
    void onVideoTaskCreated_供应商completed_发通知() {
        AiTask task = videoTask(2L, "account", "u1");
        when(aiTaskMapper.selectById(2L)).thenReturn(task);
        AiVideoStatusVO vo = new AiVideoStatusVO();
        vo.setStatus("completed");
        when(aiVideoService.getTask(1L, "vid-2")).thenReturn(vo);

        worker.onVideoTaskCreated(new VideoTaskCreatedEvent(2L, 1L, "vid-2"));

        ArgumentCaptor<AiNotifyMessage> cap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(cap.capture());
        assertEquals("account:u1", cap.getValue().subject());
        assertEquals("video", cap.getValue().type());
        assertEquals("completed", cap.getValue().status());
    }

    @Test
    void onVideoTaskCreated_供应商failed_发通知() {
        AiTask task = videoTask(3L, "ip", "1.2.3.4");
        when(aiTaskMapper.selectById(3L)).thenReturn(task);
        AiVideoStatusVO vo = new AiVideoStatusVO();
        vo.setStatus("failed");
        when(aiVideoService.getTask(1L, "vid-3")).thenReturn(vo);

        worker.onVideoTaskCreated(new VideoTaskCreatedEvent(3L, 1L, "vid-3"));

        ArgumentCaptor<AiNotifyMessage> cap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(cap.capture());
        assertEquals("failed", cap.getValue().status());
    }

    @Test
    void onVideoTaskCreated_轮询异常_继续轮询至完成() {
        AiTask task = videoTask(4L, "account", "u2");
        when(aiTaskMapper.selectById(4L)).thenReturn(task);
        AiVideoStatusVO inProgress = new AiVideoStatusVO();
        inProgress.setStatus("in_progress");
        AiVideoStatusVO completed = new AiVideoStatusVO();
        completed.setStatus("completed");
        when(aiVideoService.getTask(1L, "vid-4"))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(inProgress)
                .thenReturn(completed);

        worker.onVideoTaskCreated(new VideoTaskCreatedEvent(4L, 1L, "vid-4"));

        ArgumentCaptor<AiNotifyMessage> cap = ArgumentCaptor.forClass(AiNotifyMessage.class);
        verify(publisher).publish(cap.capture());
        assertEquals("completed", cap.getValue().status());
        assertEquals(4L, cap.getValue().taskId());
    }
}
