package com.stellar.dto;

import lombok.Data;

@Data
public class AiConfigDTO {
    private String endpoint;

    private String apiKey;

    private String model;

    /** 神奇海螺 AI 匹配开关: 0关闭 1开启 */
    private Integer conchAiEnabled;
}