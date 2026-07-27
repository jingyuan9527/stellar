package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiDocumentAddDTO {

    @NotBlank(message = "文档内容不能为空")
    private String text;

    private String sourceName;
}
