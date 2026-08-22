package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 长期记忆更新请求（仅允许改内容）
 */
@Data
public class AiMemoryUpdateDTO {

    @NotBlank(message = "记忆内容不能为空")
    @Size(max = 20000, message = "记忆内容过长")
    private String content;
}
