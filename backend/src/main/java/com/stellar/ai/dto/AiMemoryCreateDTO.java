package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动新增长期记忆请求（管理员指定用户与内容）
 */
@Data
public class AiMemoryCreateDTO {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "记忆内容不能为空")
    @Size(max = 20000, message = "记忆内容过长")
    private String content;
}
