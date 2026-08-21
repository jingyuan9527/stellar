package com.stellar.ai.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.service.AiTaskService;
import com.stellar.tts.port.TtsHistoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link TtsHistoryStoreAdapter} 单测：TtsHistoryEntry ↔ AiTask 映射与存储透传。
 */
@ExtendWith(MockitoExtension.class)
class TtsHistoryStoreAdapterTest {

    @Mock
    AiTaskService aiTaskService;

    @Test
    void record_映射为tts任务() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        LocalDateTime now = LocalDateTime.now();
        adapter.record(new TtsHistoryEntry(null, "hello", new byte[]{1, 2}, 2L, "mp3",
                "{\"voice\":\"v\"}", "ip", "1.2.3.4", now, now));

        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskService).record(cap.capture());
        AiTask t = cap.getValue();
        assertEquals("tts", t.getTaskType());
        assertEquals("success", t.getStatus());
        assertEquals("hello", t.getPrompt());
        assertEquals("mp3", t.getAudioFormat());
        assertEquals(2L, t.getFileSize());
        assertEquals("ip", t.getSubjectType());
        assertEquals("1.2.3.4", t.getSubjectId());
        assertEquals("{\"voice\":\"v\"}", t.getExtra());
        assertEquals(now, t.getRequestTime());
    }

    @Test
    void record_时间空_自动补当前时间() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        adapter.record(new TtsHistoryEntry(null, "t", new byte[]{1}, 1L, "wav",
                null, "account", "u1", null, null));
        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskService).record(cap.capture());
        assertNotNull(cap.getValue().getRequestTime());
    }

    @Test
    void page_映射分页结果() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        AiTask task = new AiTask();
        task.setId(1L);
        task.setPrompt("text");
        task.setAudioFormat("mp3");
        task.setSubjectId("u1");
        task.setExtra("{\"voice\":\"v\"}");
        Page<AiTask> result = new Page<>(1, 10, 1);
        result.setPages(1);
        result.setRecords(List.of(task));
        when(aiTaskService.pageByType(eq("tts"), any(), any(), any(), anyInt(), anyInt())).thenReturn(result);

        var page = adapter.page(null, null, null, 1, 10);
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("text", page.getRecords().get(0).text());
    }

    @Test
    void findById_不存在_返回null() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        when(aiTaskService.getById(9L)).thenReturn(null);
        assertNull(adapter.findById(9L));
    }

    @Test
    void findById_存在_返回完整条目() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        AiTask task = new AiTask();
        task.setId(9L);
        task.setPrompt("t");
        task.setFileData(new byte[]{7});
        task.setAudioFormat("wav");
        when(aiTaskService.getById(9L)).thenReturn(task);
        TtsHistoryEntry entry = adapter.findById(9L);
        assertEquals(9L, entry.id());
        assertArrayEquals(new byte[]{7}, entry.audioData());
        assertEquals("wav", entry.audioFormat());
    }

    @Test
    void deleteById_透传() {
        TtsHistoryStoreAdapter adapter = new TtsHistoryStoreAdapter(aiTaskService);
        adapter.deleteById(5L);
        verify(aiTaskService).deleteById(5L);
    }
}