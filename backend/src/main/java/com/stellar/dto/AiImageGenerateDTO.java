package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiImageGenerateDTO {

    @NotNull(message = "模型不能为空")
    private Long modelId;

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /** 可选，如 1024x1024 / 512x512，未传用 1024x1024 */
    private String size;
}
