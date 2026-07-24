package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiTemplateDTO {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过100字")
    private String name;

    @NotBlank(message = "平台不能为空")
    private String platform;

    @NotBlank(message = "提示词不能为空")
    private String prompt;
}
