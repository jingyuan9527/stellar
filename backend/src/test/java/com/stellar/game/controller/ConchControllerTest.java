package com.stellar.game.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.game.dto.ConchAnswerDTO;
import com.stellar.game.dto.ConchAnswerQueryDTO;
import com.stellar.game.dto.ConchAskDTO;
import com.stellar.game.service.ConchService;
import com.stellar.game.vo.ConchAnswerVO;
import com.stellar.game.vo.ConchAskResultVO;
import com.stellar.game.vo.ConchRecordVO;
import com.stellar.system.entity.SysFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link ConchController} 单测：提问透传、音频读取响应头（Content-Type/Cache/Content-Disposition）、
 * 预设 CRUD 与历史分页透传。
 */
@ExtendWith(MockitoExtension.class)
class ConchControllerTest {

    @Mock
    ConchService conchService;

    ConchController controller;

    @BeforeEach
    void setup() {
        controller = new ConchController(conchService);
    }

    @Test
    void ask_正常() {
        ConchAskDTO dto = new ConchAskDTO();
        dto.setQuestion("今天运气怎么样");
        ConchAskResultVO vo = new ConchAskResultVO();
        vo.setAnswerText("很好");
        when(conchService.ask(dto)).thenReturn(vo);
        assertEquals("很好", controller.ask(dto).getData().getAnswerText());
    }

    @Test
    void answerAudio_正常_响应头齐全() {
        SysFile f = new SysFile();
        f.setContentType("audio/mpeg");
        f.setExt("mp3");
        f.setData(new byte[]{1, 2, 3});
        when(conchService.getAnswerFile(5L)).thenReturn(f);

        ResponseEntity<byte[]> resp = controller.answerAudio(5L);

        assertEquals("audio/mpeg", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("public, max-age=604800", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("conch_5.mp3"));
        assertEquals(3, resp.getBody().length);
    }

    @Test
    void answerAudio_contentType为空_兜底audioMpeg() {
        SysFile f = new SysFile();
        f.setContentType(null);
        f.setExt("mp3");
        f.setData(new byte[]{1});
        when(conchService.getAnswerFile(5L)).thenReturn(f);
        assertEquals("audio/mpeg", controller.answerAudio(5L).getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void answerPage_正常() {
        ConchAnswerQueryDTO q = new ConchAnswerQueryDTO();
        when(conchService.answerPage(q)).thenReturn(new Page<ConchAnswerVO>());
        assertNotNull(controller.answerPage(q).getData());
    }

    @Test
    void createAnswer_正常() {
        ConchAnswerDTO dto = new ConchAnswerDTO();
        controller.createAnswer(dto);
        verify(conchService).createAnswer(dto);
    }

    @Test
    void updateAnswer_正常() {
        ConchAnswerDTO dto = new ConchAnswerDTO();
        controller.updateAnswer(dto);
        verify(conchService).updateAnswer(dto);
    }

    @Test
    void deleteAnswer_正常() {
        controller.deleteAnswer(3L);
        verify(conchService).deleteAnswer(3L);
    }

    @Test
    void toggleEnabled_正常() {
        controller.toggleEnabled(3L, 1);
        verify(conchService).toggleEnabled(3L, 1);
    }

    @Test
    void recordPage_正常() {
        when(conchService.recordPage(1, 10)).thenReturn(new Page<ConchRecordVO>());
        assertNotNull(controller.recordPage(1, 10).getData());
    }
}
