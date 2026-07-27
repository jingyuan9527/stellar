package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiChatStreamDTO {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "消息不能为空")
    private String userMessage;

    /** 可选：TEXT 模型 ID，为空用默认 */
    private Long modelId;
}
