package com.stellar.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatSessionCreateDTO {

    private Long personaId;

    private Long kbId;

    @Size(max = 100, message = "会话标题最长 100 字")
    private String title;
}
