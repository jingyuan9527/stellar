package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiImageGenerateDTO {

    @NotNull(message = "模型不能为空")
    private Long modelId;

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /** 尺寸档位，如 1K/2K/3K/4K，未传用 1K */
    private String size;

    /** 宽高比，如 1:1/16:9/9:16，未传用 1:1 */
    private String ratio;
}
