package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "提示词不能为空")
    private String prompt;
}
