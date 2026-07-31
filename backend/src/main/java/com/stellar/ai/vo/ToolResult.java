package com.stellar.ai.vo;

import com.stellar.ai.service.AiChatService;
import com.stellar.ai.service.AiChatToolService;

/**
 * AI 聊天工具调用执行结果。
 * <p>由 {@code AiChatToolService.execute} 返回，供 AiChatService 编排二次流式：
 * content 作为 tool 角色 message 回传给 LLM（JSON 字符串含 status/type/fileId/url），
 * attachmentType/attachmentFileId 用于挂在最终 assistant 消息上持久化。
 *
 * @param toolCallId       LLM 返回的 tool_call id，用于构造 tool message
 * @param content          回传给 LLM 的 tool message content（JSON 字符串）
 * @param attachmentType   附件类型 image/audio；工具失败或无产物为 null
 * @param attachmentFileId 附件 sys_file.id；工具失败或无产物为 null
 */
public record ToolResult(
        String toolCallId,
        String content,
        String attachmentType,
        Long attachmentFileId
) {
}
