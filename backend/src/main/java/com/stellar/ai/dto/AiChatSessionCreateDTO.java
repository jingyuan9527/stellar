package com.stellar.ai.dto;

import lombok.Data;

@Data
public class AiChatSessionCreateDTO {

    private Long personaId;

    private Long kbId;

    private String title;
}
