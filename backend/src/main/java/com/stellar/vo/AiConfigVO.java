package com.stellar.vo;

import lombok.Data;

/**
 * AI 配置视图，API Key 脱敏后返回前端。
 */
@Data
public class AiConfigVO {

    private String endpoint;

    /** 脱敏后的 API Key，如 sk-****abcd */
    private String apiKey;

    private String model;

    /** endpoint + apiKey + model 均非空时为 true */
    private boolean configured;
}
