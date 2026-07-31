package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiPersonaDTO {

    private Long id;

    @NotBlank(message = "人设名称不能为空")
    private String name;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    private String description;

    private Integer enabled;

    private Integer sortOrder;
}
