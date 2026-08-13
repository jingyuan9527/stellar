package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.ai.dto.AiChatFeedbackDTO;
import com.stellar.ai.dto.AiChatSessionCreateDTO;
import com.stellar.ai.dto.AiChatStreamDTO;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.AiChatSession;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天会话：建会话/列表/消息/删除（游客按 IP），流式聊天（IP 限流）。
 * <p>聊天页对游客开放：会话与消息按 IP 归属校验；管理页查所有会话需登录。
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatSessionController {

    private final AiChatSessionService sessionService;

    /**
     * 创建会话（对游客开放，游客的 kbId 强制为空）。
     */
    @PublicAccess
    @PostMapping("/session")
    @Log(title = "AI聊天会话", type = OperationType.INSERT)
    public Result<AiChatSession> createSession(@RequestBody AiChatSessionCreateDTO dto) {
        return Result.success(sessionService.createSession(
                dto.getPersonaId(), dto.getKbId(), dto.getTitle()));
    }

    /**
     * 当前主体的会话列表（对游客开放，按 IP）。
     */
    @PublicAccess
    @GetMapping("/session")
    @Log(title = "AI聊天会话", type = OperationType.QUERY)
    public Result<List<AiChatSession>> listMySessions() {
        return Result.success(sessionService.listMySessions());
    }

    /**
     * 管理后台分页查所有会话（需登录）。
     */
    @GetMapping("/session/all")
    @Log(title = "AI聊天会话", type = OperationType.QUERY)
    public Result<Page<Map<String, Object>>> pageAllSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(sessionService.pageAllSessions(pageNum, pageSize));
    }

    /**
     * 会话消息（对游客开放，按归属校验）。
     */
    @PublicAccess
    @GetMapping("/session/{id}/messages")
    @Log(title = "AI聊天消息", type = OperationType.QUERY)
    public Result<List<AiChatMessage>> getMessages(@PathVariable Long id) {
        return Result.success(sessionService.getMessages(id));
    }

    @PublicAccess
    @PutMapping("/session/{id}")
    @Log(title = "AI聊天会话", type = OperationType.UPDATE)
    public Result<Void> updateSession(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sessionService.updateSession(id, body.get("title"));
        return Result.success();
    }

    @PublicAccess
    @DeleteMapping("/session/{id}")
    @Log(title = "AI聊天会话", type = OperationType.DELETE)
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return Result.success();
    }

    /**
     * 管理员查看任意会话消息（需登录，无归属校验）。
     */
    @GetMapping("/session/{id}/messages/admin")
    @Log(title = "AI聊天消息", type = OperationType.QUERY)
    public Result<List<AiChatMessage>> getMessagesAdmin(@PathVariable Long id) {
        return Result.success(sessionService.getMessagesAdmin(id));
    }

    /**
     * 管理员删除任意会话（需登录，无归属校验）。
     */
    @DeleteMapping("/session/{id}/admin")
    @Log(title = "AI聊天会话", type = OperationType.DELETE)
    public Result<Void> deleteSessionAdmin(@PathVariable Long id) {
        sessionService.deleteSessionAdmin(id);
        return Result.success();
    }

    /**
     * 清空当前主体的全部会话（对游客开放）。
     */
    @PublicAccess
    @DeleteMapping("/session")
    @Log(title = "AI聊天会话", type = OperationType.DELETE)
    public Result<Integer> deleteMySessions() {
        return Result.success(sessionService.deleteMySessions());
    }

    /**
     * 多轮流式聊天（对游客开放，按 IP 单日限流 20 次）。
     * <p>登录用户带 tools（画图/TTS function calling）；游客纯文本。
     * voice 为 TTS 音色（仅登录用户工具调用生效）。
     */
    @PublicAccess
    @RateLimit(daily = 20)
    @PostMapping("/session/stream")
    @Log(title = "AI聊天", type = OperationType.OTHER)
    public SseEmitter streamChat(@Valid @RequestBody AiChatStreamDTO dto) {
        return sessionService.streamChat(dto.getSessionId(), dto.getUserMessage(), dto.getModelId(), dto.getVoice());
    }

    /**
     * 回复反馈（👍/👎）：对某条消息打分（1 有用 / -1 没用 / 0 取消评价），同一主体重复打分会覆盖。
     * <p>游客开放（按 IP 归属校验），评分是数据飞轮评估集原料（期4 复盘用）。
     */
    @PublicAccess
    @PostMapping("/feedback")
    @Log(title = "AI聊天反馈", type = OperationType.INSERT)
    public Result<Void> feedback(@Valid @RequestBody AiChatFeedbackDTO dto) {
        sessionService.saveFeedback(dto);
        return Result.success();
    }
}
