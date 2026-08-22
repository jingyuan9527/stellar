package com.stellar.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 聊天会话重命名请求（与 AiChatSessionCreateDTO 的标题约束保持一致）
 */
@Data
public class AiChatSessionUpdateDTO {

    @Size(max = 100, message = "会话标题最长 100 字")
    private String title;
}
