package com.stellar.tts.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import com.stellar.tts.port.TtsHistoryEntry;
import com.stellar.tts.port.TtsHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TtsRecordService} 单测：save/saveAiTts/saveChatTts 三种落库格式、
 * page 转 VO、getAudio 不存在抛、deleteById、extra 序列化与解析。
 * 落 ai_task 的映射见 ai 侧 {@code TtsHistoryStoreAdapter} 单测。
 */
@ExtendWith(MockitoExtension.class)
class TtsRecordServiceTest {

    @Mock
    TtsHistoryStore ttsHistoryStore;

    private TtsRecordService service() {
        return new TtsRecordService(ttsHistoryStore, new ObjectMapper());
    }

    @Test
    void save_edge_落mp3记录() {
        TtsRecordService service = service();
        TtsRequest req = new TtsRequest();
        req.setText("hello");
        req.setVoice("zh-CN-XiaoxiaoNeural");
        service.save(req, new byte[]{1, 2}, "ip", "1.2.3.4");

        ArgumentCaptor<TtsHistoryEntry> cap = ArgumentCaptor.forClass(TtsHistoryEntry.class);
        verify(ttsHistoryStore).record(cap.capture());
        TtsHistoryEntry e = cap.getValue();
        assertEquals("mp3", e.audioFormat());
        assertEquals("hello", e.text());
        assertArrayEquals(new byte[]{1, 2}, e.audioData());
        assertEquals(2L, e.fileSize());
        assertEquals("ip", e.subjectType());
        assertTrue(e.extra().contains("\"voice\":\"zh-CN-XiaoxiaoNeural\""));
        assertNotNull(e.requestTime());
        assertNotNull(e.createTime());
    }

    @Test
    void saveAiTts_落wav记录() {
        TtsRecordService service = service();
        service.saveAiTts("hi", "mimo-1", new byte[]{9}, "account", "u1");

        ArgumentCaptor<TtsHistoryEntry> cap = ArgumentCaptor.forClass(TtsHistoryEntry.class);
        verify(ttsHistoryStore).record(cap.capture());
        assertEquals("wav", cap.getValue().audioFormat());
        assertEquals("account", cap.getValue().subjectType());
    }

    @Test
    void saveChatTts_自定义格式() {
        TtsRecordService service = service();
        service.saveChatTts("t", "v", new byte[]{3}, "mp3", "ip", "9.9.9.9");
        ArgumentCaptor<TtsHistoryEntry> cap = ArgumentCaptor.forClass(TtsHistoryEntry.class);
        verify(ttsHistoryStore).record(cap.capture());
        assertEquals("mp3", cap.getValue().audioFormat());
    }

    @Test
    void page_转VO解析voice() {
        TtsRecordService service = service();
        TtsHistoryEntry entry = new TtsHistoryEntry(1L, "text", new byte[]{1},
                10L, "mp3", "{\"voice\":\"zh-CN-XiaoxiaoNeural\",\"rate\":1.0}",
                "account", "u1", LocalDateTime.now(), LocalDateTime.now());
        Page<TtsHistoryEntry> page = new Page<>(1, 10, 1);
        page.setPages(1);
        page.setRecords(List.of(entry));

        when(ttsHistoryStore.page(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        TtsRecordQueryDTO q = new TtsRecordQueryDTO();
        Page<TtsRecord> result = service.page(q);
        assertEquals(1, result.getRecords().size());
        TtsRecord r = result.getRecords().get(0);
        assertEquals("text", r.getText());
        assertEquals("mp3", r.getAudioFormat());
        assertEquals("zh-CN-XiaoxiaoNeural", r.getVoice());
        assertEquals("u1", r.getOperator());
    }

    @Test
    void getAudio_不存在_抛() {
        TtsRecordService service = service();
        when(ttsHistoryStore.findById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAudio(1L));
    }

    @Test
    void getAudio_无音频数据_抛() {
        TtsRecordService service = service();
        TtsHistoryEntry entry = new TtsHistoryEntry(1L, "t", null, null, "wav",
                null, "account", "u1", null, null);
        when(ttsHistoryStore.findById(1L)).thenReturn(entry);
        assertThrows(BusinessException.class, () -> service.getAudio(1L));
    }

    @Test
    void getAudio_正常_返回音频与格式() {
        TtsRecordService service = service();
        TtsHistoryEntry entry = new TtsHistoryEntry(1L, "t", new byte[]{7, 8}, 2L, "wav",
                null, "account", "u1", null, null);
        when(ttsHistoryStore.findById(1L)).thenReturn(entry);

        TtsRecord r = service.getAudio(1L);
        assertArrayEquals(new byte[]{7, 8}, r.getAudioData());
        assertEquals("wav", r.getAudioFormat());
    }

    @Test
    void deleteById_删除() {
        TtsRecordService service = service();
        service.deleteById(1L);
        verify(ttsHistoryStore).deleteById(1L);
    }
}