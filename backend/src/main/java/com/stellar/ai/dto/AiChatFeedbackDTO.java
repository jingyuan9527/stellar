package com.stellar.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 聊天回复反馈请求：评价值 1 有用 / -1 没用 / 0 取消评价，附可选说明。
 */
@Data
public class AiChatFeedbackDTO {

    /** 被评价消息ID(引用 ai_chat_message.id) */
    @NotNull(message = "messageId 不能为空")
    private Long messageId;

    /** 1 有用 / -1 没用 / 0 取消评价 */
    @NotNull(message = "value 不能为空")
    private Integer value;

    /** 可选补充说明 */
    private String comment;
}
