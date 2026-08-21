package com.stellar.tts.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.tts.dto.AiTtsRequest;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import com.stellar.tts.service.AiTtsService;
import com.stellar.tts.service.TtsRecordService;
import com.stellar.tts.service.TtsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TtsController} 单测：Edge/AI 合成响应头与记录保存（失败不影响合成）、历史分页与音频读取的
 * audio_format 动态 Content-Type、删除透传。
 */
@ExtendWith(MockitoExtension.class)
class TtsControllerTest {

    @Mock
    TtsService ttsService;
    @Mock
    TtsRecordService ttsRecordService;
    @Mock
    AiTtsService aiTtsService;
    @Mock
    HttpServletRequest servletRequest;

    TtsController controller;

    @BeforeEach
    void setup() {
        controller = new TtsController(ttsService, ttsRecordService, aiTtsService);
    }

    @Test
    void synthesize_正常_保存记录() {
        TtsRequest req = new TtsRequest();
        req.setText("你好");
        req.setVoice("zh-CN-XiaoxiaoNeural");
        when(ttsService.synthesize("你好", "zh-CN-XiaoxiaoNeural", 1.0, 1.0, 1.0))
                .thenReturn(new byte[]{1, 2});
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            ResponseEntity<byte[]> resp = controller.synthesize(req, servletRequest);
            assertEquals("audio/mpeg", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            verify(ttsRecordService).save(eq(req), eq(new byte[]{1, 2}), eq("account"), eq("u1"));
        }
    }

    @Test
    void synthesize_保存记录失败_不影响合成结果() {
        TtsRequest req = new TtsRequest();
        req.setText("hi");
        req.setVoice("v");
        when(ttsService.synthesize("hi", "v", 1.0, 1.0, 1.0)).thenReturn(new byte[]{9});
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        doThrow(new RuntimeException("db down")).when(ttsRecordService)
                .save(eq(req), eq(new byte[]{9}), eq("ip"), eq("8.8.8.8"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            ResponseEntity<byte[]> resp = controller.synthesize(req, servletRequest);
            assertEquals(1, resp.getBody().length);
        }
    }

    @Test
    void recordPage_正常() {
        TtsRecordQueryDTO q = new TtsRecordQueryDTO();
        when(ttsRecordService.page(q)).thenReturn(new Page<>());
        assertNotNull(controller.recordPage(q).getData());
    }

    @Test
    void aiSynthesize_正常_保存AI记录() {
        AiTtsRequest req = new AiTtsRequest();
        req.setModelId(2L);
        req.setText("你好");
        req.setVoice("冰糖");
        req.setStyle("温柔");
        when(aiTtsService.synthesize(2L, "你好", "冰糖", "温柔")).thenReturn(new byte[]{1});
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            ResponseEntity<byte[]> resp = controller.aiSynthesize(req, servletRequest);
            assertEquals("audio/wav", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            verify(ttsRecordService).saveAiTts("你好", "冰糖", new byte[]{1}, "ip", "8.8.8.8");
        }
    }

    @Test
    void recordAudio_wav格式_设audioWav() {
        TtsRecord rec = new TtsRecord();
        rec.setAudioFormat("wav");
        rec.setAudioData(new byte[]{1, 2});
        when(ttsRecordService.getAudio(7L)).thenReturn(rec);

        ResponseEntity<byte[]> resp = controller.recordAudio(7L);

        assertEquals("audio/wav", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("tts_7.wav"));
    }

    @Test
    void recordAudio_默认mp3() {
        TtsRecord rec = new TtsRecord();
        rec.setAudioFormat(null);
        rec.setAudioData(new byte[]{1});
        when(ttsRecordService.getAudio(7L)).thenReturn(rec);

        ResponseEntity<byte[]> resp = controller.recordAudio(7L);

        assertEquals("audio/mpeg", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("tts_7.mp3"));
    }

    @Test
    void deleteRecord_正常() {
        controller.deleteRecord(3L);
        verify(ttsRecordService).deleteById(3L);
    }
}
