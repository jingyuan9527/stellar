package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
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
 * AI 流式聊天 + token 统计。
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 流式聊天。对游客开放（@PublicAccess），按 IP 单日限 5 次（@RateLimit）。
     * 每次调用记录 token 消费（登录按账号、游客按 IP）。
     */
    @PublicAccess
    @RateLimit(daily = 5)
    @PostMapping("/stream")
    @Log(title = "AI对话", type = OperationType.OTHER)
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        return aiChatService.streamChat(request);
    }
}
