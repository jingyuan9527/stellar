package com.stellar.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 供应商视图，API Key 脱敏后返回前端。
 */
@Data
public class AiProviderVO {

    private Long id;

    private String name;

    private String endpoint;

    /** 脱敏后的 API Key */
    private String apiKey;

    /** 最近拉取到的可用模型列表 */
    private List<String> availableModels;

    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
