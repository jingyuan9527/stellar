package com.stellar.ai.vo;

import com.stellar.ai.service.AiChatService;
import com.stellar.ai.service.AiChatSessionService;

/**
 * AI 聊天流式完成回调结果。
 * <p>{@code AiChatService.streamMultiChatWithTools} 流式结束时回调 {@code onComplete(AiChatResult)}，
 * 供 {@code AiChatSessionService} 落 assistant 消息：content 为最终文本，
 * attachmentType/attachmentFileId 为工具产物（若有），挂在同一条 assistant 消息上。
 *
 * @param content          LLM 最终流式文本（游客路径或无工具调用时也走此回调）
 * @param attachmentType   附件类型 image/audio；无工具产物为 null
 * @param attachmentFileId 附件 sys_file.id；无工具产物为 null
 */
public record AiChatResult(
        String content,
        String attachmentType,
        Long attachmentFileId
) {
}
