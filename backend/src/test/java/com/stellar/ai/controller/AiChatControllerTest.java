package com.stellar.ai.controller;

import com.stellar.ai.dto.ChatRequest;
import com.stellar.ai.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * {@link AiChatController} 单测：纯转发，验证请求参数透传与返回值原样返回。
 */
@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {

    @Mock
    AiChatService aiChatService;

    AiChatController controller;

    @BeforeEach
    void setup() {
        controller = new AiChatController(aiChatService);
    }

    @Test
    void streamChat_正常_透传请求返回emitter() {
        ChatRequest req = new ChatRequest();
        req.setPrompt("写个段子");
        req.setModelId(7L);
        req.setEndpoint("https://x");
        req.setApiKey("k");
        req.setModel("m");
        SseEmitter emitter = new SseEmitter();
        when(aiChatService.streamChat(req)).thenReturn(emitter);

        SseEmitter result = controller.streamChat(req);

        assertSame(emitter, result);
        verify(aiChatService).streamChat(req);
    }
}
