package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /** 可选：用户自带 AI 配置（前端 localStorage 传入，后端不持久化） */
    private String endpoint;

    private String apiKey;

    private String model;
}
