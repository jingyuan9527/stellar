package com.stellar.tts.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.common.BusinessException;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TtsRecordService} 单测：save/saveAiTts/saveChatTts 三种落库格式、
 * page 转 VO、getAudio 不存在抛、deleteById、extra 序列化与解析。
 */
@ExtendWith(MockitoExtension.class)
class TtsRecordServiceTest {

    @Mock
    AiTaskMapper aiTaskMapper;

    private TtsRecordService service() {
        return new TtsRecordService(aiTaskMapper, new ObjectMapper());
    }

    @Test
    void save_edge_落mp3记录() {
        TtsRecordService service = service();
        TtsRequest req = new TtsRequest();
        req.setText("hello");
        req.setVoice("zh-CN-XiaoxiaoNeural");
        service.save(req, new byte[]{1, 2}, "ip", "1.2.3.4");

        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).insert(cap.capture());
        AiTask t = cap.getValue();
        assertEquals("tts", t.getTaskType());
        assertEquals("mp3", t.getAudioFormat());
        assertEquals("hello", t.getPrompt());
        assertArrayEquals(new byte[]{1, 2}, t.getFileData());
        assertEquals(2L, t.getFileSize());
        assertTrue(t.getExtra().contains("\"voice\":\"zh-CN-XiaoxiaoNeural\""));
    }

    @Test
    void saveAiTts_落wav记录() {
        TtsRecordService service = service();
        service.saveAiTts("hi", "mimo-1", new byte[]{9}, "account", "u1");

        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).insert(cap.capture());
        assertEquals("wav", cap.getValue().getAudioFormat());
        assertEquals("account", cap.getValue().getSubjectType());
    }

    @Test
    void saveChatTts_自定义格式() {
        TtsRecordService service = service();
        service.saveChatTts("t", "v", new byte[]{3}, "mp3", "ip", "9.9.9.9");
        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).insert(cap.capture());
        assertEquals("mp3", cap.getValue().getAudioFormat());
    }

    @Test
    void page_转VO解析voice() {
        TtsRecordService service = service();
        AiTask task = new AiTask();
        task.setId(1L);
        task.setTaskType("tts");
        task.setPrompt("text");
        task.setFileSize(10L);
        task.setAudioFormat("mp3");
        task.setSubjectId("u1");
        task.setExtra("{\"voice\":\"zh-CN-XiaoxiaoNeural\",\"rate\":1.0}");
        Page<AiTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task));

        when(aiTaskMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

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
        when(aiTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAudio(1L));
    }

    @Test
    void getAudio_无音频数据_抛() {
        TtsRecordService service = service();
        AiTask task = new AiTask();
        task.setId(1L);
        when(aiTaskMapper.selectById(1L)).thenReturn(task);
        assertThrows(BusinessException.class, () -> service.getAudio(1L));
    }

    @Test
    void getAudio_正常_返回音频与格式() {
        TtsRecordService service = service();
        AiTask task = new AiTask();
        task.setId(1L);
        task.setFileData(new byte[]{7, 8});
        task.setAudioFormat("wav");
        when(aiTaskMapper.selectById(1L)).thenReturn(task);

        TtsRecord r = service.getAudio(1L);
        assertArrayEquals(new byte[]{7, 8}, r.getAudioData());
        assertEquals("wav", r.getAudioFormat());
    }

    @Test
    void deleteById_删除() {
        TtsRecordService service = service();
        service.deleteById(1L);
        verify(aiTaskMapper).deleteById(1L);
    }
}
