package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.dto.ChatRequest;
import com.stellar.enums.OperationType;
import com.stellar.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 流式聊天，通过 SseEmitter 转发 LLM 的 SSE 流。
 * <p>
 * 前端用 fetch + ReadableStream 读取（可带 Authorization header，绕过 EventSource 限制）。
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/stream")
    @Log(title = "AI对话", type = OperationType.OTHER)
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        return aiChatService.streamChat(request.getPrompt());
    }
}
