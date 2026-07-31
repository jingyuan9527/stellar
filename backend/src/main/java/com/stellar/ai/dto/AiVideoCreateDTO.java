package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiVideoCreateDTO {

    @NotNull(message = "模型不能为空")
    private Long modelId;

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /** 画面比例，如 16:9/9:16/1:1（仅历史展示用，可选） */
    private String ratio;

    /** 时长(秒)，仅历史展示用，可选 */
    private Integer duration;

    private Integer width;

    private Integer height;

    /** 帧数，须 ≤441 且遵循 8n+1 */
    private Integer numFrames;

    private Double frameRate;
}
