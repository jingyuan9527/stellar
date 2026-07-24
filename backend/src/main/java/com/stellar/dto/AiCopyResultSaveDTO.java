package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCopyResultSaveDTO {

    @NotBlank(message = "主题不能为空")
    private String topic;

    private Long templateId;

    @NotBlank(message = "结果不能为空")
    private String result;
}
