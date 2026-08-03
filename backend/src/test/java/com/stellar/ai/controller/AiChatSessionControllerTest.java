package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.AiChatSessionCreateDTO;
import com.stellar.ai.dto.AiChatStreamDTO;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.AiChatSession;
import com.stellar.ai.service.AiChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiChatSessionController} 单测：会话/消息 CRUD 与流式聊天的参数透传、返回值校验。
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionControllerTest {

    @Mock
    AiChatSessionService sessionService;

    AiChatSessionController controller;

    @BeforeEach
    void setup() {
        controller = new AiChatSessionController(sessionService);
    }

    @Test
    void createSession_正常_透传三参() {
        AiChatSessionCreateDTO dto = new AiChatSessionCreateDTO();
        dto.setPersonaId(1L);
        dto.setKbId(2L);
        dto.setTitle("t");
        AiChatSession s = new AiChatSession();
        s.setId(9L);
        when(sessionService.createSession(1L, 2L, "t")).thenReturn(s);

        AiChatSession r = controller.createSession(dto).getData();

        assertEquals(9L, r.getId());
        verify(sessionService).createSession(1L, 2L, "t");
    }

    @Test
    void listMySessions_正常() {
        when(sessionService.listMySessions()).thenReturn(List.of(new AiChatSession()));
        assertEquals(1, controller.listMySessions().getData().size());
    }

    @Test
    void pageAllSessions_正常() {
        when(sessionService.pageAllSessions(1, 20)).thenReturn(new Page<>());
        assertNotNull(controller.pageAllSessions(1, 20).getData());
        verify(sessionService).pageAllSessions(1, 20);
    }

    @Test
    void getMessages_正常() {
        when(sessionService.getMessages(5L)).thenReturn(List.of(new AiChatMessage()));
        assertEquals(1, controller.getMessages(5L).getData().size());
    }

    @Test
    void updateSession_正常() {
        controller.updateSession(1L, Map.of("title", "新标题"));
        verify(sessionService).updateSession(1L, "新标题");
    }

    @Test
    void deleteSession_正常() {
        controller.deleteSession(1L);
        verify(sessionService).deleteSession(1L);
    }

    @Test
    void getMessagesAdmin_正常() {
        when(sessionService.getMessagesAdmin(5L)).thenReturn(List.of(new AiChatMessage()));
        assertEquals(1, controller.getMessagesAdmin(5L).getData().size());
    }

    @Test
    void deleteSessionAdmin_正常() {
        controller.deleteSessionAdmin(1L);
        verify(sessionService).deleteSessionAdmin(1L);
    }

    @Test
    void deleteMySessions_正常() {
        when(sessionService.deleteMySessions()).thenReturn(3);
        assertEquals(3, controller.deleteMySessions().getData());
    }

    @Test
    void streamChat_正常_透传参数返回emitter() {
        AiChatStreamDTO dto = new AiChatStreamDTO();
        dto.setSessionId(1L);
        dto.setUserMessage("你好");
        dto.setModelId(7L);
        dto.setVoice("zh-CN-XiaoxiaoNeural");
        SseEmitter emitter = new SseEmitter();
        when(sessionService.streamChat(1L, "你好", 7L, "zh-CN-XiaoxiaoNeural")).thenReturn(emitter);

        SseEmitter r = controller.streamChat(dto);

        assertSame(emitter, r);
        verify(sessionService).streamChat(1L, "你好", 7L, "zh-CN-XiaoxiaoNeural");
    }
}
